# Análisis uso de configmaps en diferentes frameworks java
A continuación, se va a proceder a analizar qué nos ofrecen actualmente los principales frameworks de java orientados a microservicios y cloud a la hora de interactuar con configmaps en kubernetes, evaluando la posibilidad de detectar cambios "en caliente".

En todos los frameworks, siempre se tendrá un archivo `application.properties` o `application.yaml`, donde se guardará la configuración de la aplicación (desde los parámetros de configuración a un datasource, a la frecuencia con la que se ejecuta un timer). A la hora de desplegar la aplicación en un entorno en concreto, las variables de entorno sobrescribirán dichas propiedades; es decir, si en el archivo `application.properties` existe una propiedad `properties.fubar.foo`, y al desplegar la aplicación, existe una variable de entorno `PROPERTIES_FUBAR_FOO` configurada, ésta sobrescribirá el valor de la variable en el archivo properties. Esto es muy útil, ya que se podrán configurar los diferentes parámetros según el entorno en el que se despliegue la aplicación. 

Aquí es donde entran en juego los configmaps. Mediante archivos de configuración `yml`, se podrá crear y asignar en kubernetes un set de datos para uno o varios deployments, actuando como variables de entorno para los mismos (y por tanto sobreescribiendo los valores de las `properties` en caso de haberlas), haciendo que configurar los contenedores orquestados bajo kubernetes se facilite de muchas maneras.

  - [Requisitos](#requisitos)
  - [En qué consiste el análisis](#en-qué-consiste-el-análisis)
  - [Quarkus](#quarkus)
    - [Generar el proyecto](#generar-el-proyecto)
    - [Crear recursos de kubernetes y desplegar](#crear-recursos-de-kubernetes-y-desplegar)
    - [Comprobar la respuesta del endpoint y reemplazo en caliente](#comprobar-la-respuesta-del-endpoint-y-reemplazo-en-caliente)
    - [Explorar otras posibilidades de la dependencia Kubernetes](#explorar-otras-posibilidades-de-la-dependencia-kubernetes)
  - [Micronaut](#micronaut)
    - [Generar el proyecto](#generar-el-proyecto-1)
    - [Crear recursos de kubernetes y desplegar](#crear-recursos-de-kubernetes-y-desplegar-1)
    - [Comprobar la respuesta del endpoint y reemplazo en caliente](#comprobar-la-respuesta-del-endpoint-y-reemplazo-en-caliente-1)
    - [Explorar otras posibilidades de la dependencia Kubernetes](#explorar-otras-posibilidades-de-la-dependencia-kubernetes-1)
  - [Spring Boot](#spring-boot)
    - [Generar el proyecto](#generar-el-proyecto-2)
    - [Crear recursos de kubernetes y desplegar](#crear-recursos-de-kubernetes-y-desplegar-2)
    - [Comprobar la respuesta del endpoint y reemplazo en caliente](#comprobar-la-respuesta-del-endpoint-y-reemplazo-en-caliente-2)
    - [Explorar otras posibilidades de la dependencia Kubernetes](#explorar-otras-posibilidades-de-la-dependencia-kubernetes-2)


## Requisitos
    - Conocimientos básicos de kubernetes
    - Maven
    - Cluster local de kubernetes, por ejemplo Minikube
    - Kubectl
  
## En qué consiste el análisis
Los tres frameworks se pueden ejecutar con **Maven**, por lo que se podrá compilar cualquiera de ellos con el comando `mvn package` para generar los correspondientes jar. Algo que tienen también en común es que todos ellos cuentan con una web muy similar donde se pueden generar los correspondientes boilerplates, seleccionando la paquetización y las dependencias con las que el proyecto se quiere inicializar:
-	Quarkus: https://code.quarkus.io/
-	Micronaut: https://micronaut.io/launch/
-	Springboot: https://start.spring.io/

Una vez se descargan los proyectos de las correspondientes webs, y teniendo maven instalado, ya serían proyectos funcionales con las dependencias seleccionadas que se podrían ejecutar. Además, todos ellos cuentan con modo desarrollo permitiendo “hot swap” (y funciona muy bien en los tres) para tener un entorno de desarrollo ágil y rápido:
-	Quarkus: `./mvnw compile quarkus:dev`
-	Micronaut: `./mvnw mn:run`
-	Springboot: el modo desarrollo requerirá incluirá la dependencia DevTools y además arrancar la aplicación desde un IDE con plugin SpringBoot (Eclipse, IntelliJ, VS Code..).

El análisis será idéntico para cada uno de los frameworks:
1. Generar el proyecto boilerplate desde la web, añadiendo las dependencias necesarias para comunicarse con la API de kubernetes, así como servir un endpoint http. En primera instancia se devolverá por http el valor de dos propiedades configuradas en el archivo `application.properties`.
2. Crear `yamls` básicos tanto para añadir el configmap (con valores diferentes que deberán sobrescribir las propiedades) en kubernetes como para asignar los permisos necesarios al deployment para el acceso a la API de kubernetes. Despliegue en un clúster kubernetes local (se utilizará minikube para probar los ejemplos). Tanto el deployment como el service de cada aplicación se darán ya de base, puesto que no son relevantes en este análisis.
3. Comprobar si la respuesta del endpoint devuelve los valores de las variables del configmap, y no las originales del archivo properties. Adicionalmente, y si el framework lo permite, hacer un cambio en el configmap de forma manual en kubernetes y comprobar cómo la aplicación detecta el cambio y reemplaza los valores “en caliente”.
4. Por último, explorar qué otras posibilidades ofrece cada framework en relación a la dependencia de kubernetes.


De nuevo resaltar que el entorno de desarrollo ya cuenta con una instalación de minikube:
-	Instalar minikube
-	minikube start
-	minikube dashboard


## Quarkus
### Generar el proyecto 
En primer lugar se analiza Quarkus (actualmente en su versión 1.6). Tras ver en su documentación que tiene una extensión para [Kubernetes](https://quarkus.io/guides/kubernetes), se va a generar desde su web [code.quarkus.io](https://code.quarkus.io/) un boilerplate con la extensión de kubernetes y lo necesario para tener un endpoint http:

```xml
    <dependencies>
      <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-resteasy</artifactId>
      </dependency>
      <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-kubernetes</artifactId>
      </dependency>
      <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-kubernetes-config</artifactId>
      </dependency>
  </dependencies>
```

A continuación, se crearán las propiedades que se deberán sobrescribir más tarde desde el configmap en el archivo `src/main/resources/application.properties`. Además, será necesario añadir un par de propiedades extra para configurar la extensión kubernetes, tal y como indican en su guía. Una para activar la extensión, y otra para indicar qué configmaps se deben leer:

```
properties.fubar.bar=bar
properties.fubar.foo=foo
quarkus.kubernetes-config.enabled=true
quarkus.kubernetes-config.config-maps=reload-configmap
```

Ahora se añadirá un nuevo endpoint, el cual retornará el valor de las dos propiedades en cuestión, para tener un método fácil y rápido de saber qué valores tiene la aplicación para esas propiedades:

```java
@Path("/fubar")
public class PropertiesResource {

    @Inject
    public Properties properties;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getFubar() {
        return "foo: " + properties.fubar.foo + " and bar: " + properties.fubar.bar;
    }

}

@ConfigProperties(prefix = "properties") 
public class Properties {

    public Fubar fubar;

    public static class Fubar {
        public String foo;
        public String bar;
    }
}
```

### Crear recursos de kubernetes y desplegar
Por último, se añade un configmap (se puede encontrar en el directorio `quarkus/k8s/configmap.yaml` del repositorio) con nuevos valores para ambas variables:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: reload-configmap
data:
  properties.fubar.bar: bar1
  properties.fubar.foo: foo1
```

Así como un archivo `role-config.yaml` para proporcionar los permisos necesarios de lectura de la api de kubernetes:

```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  namespace: default
  name: namespace-reader
rules:
  - apiGroups: ["", "extensions", "apps"]
    resources: ["services", "endpoints", "configmaps", "secrets", "pods"]
    verbs: ["get", "watch", "list"]

---

kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: namespace-reader-binding
  namespace: default
subjects:
  - kind: ServiceAccount
    name: default
    namespace: default
roleRef:
  kind: Role
  name: namespace-reader
  apiGroup: rbac.authorization.k8s.io

---

apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: reload-configmap-view
roleRef:
  kind: ClusterRole
  name: view
  apiGroup: rbac.authorization.k8s.io
subjects:
  - kind: ServiceAccount
    name: reload-configmap
    namespace: default
```

Existe un tercer archivo `yaml`, para los recursos de deployment y service, el cuál se encuentra en la misma ruta. Por tanto, ya se puede ejecutar la aplicación en minikube una vez compilada y construida la imagen docker:

```
$ ./mvnw clean package
$ docker build -t reload-configmap:quarkus -f ./src/main/docker/Dockerfile.jvm .
$ kubectl apply –f ./k8s
```

Se podrá observar en el dashboard de minikube como hay un pod de quarkus funcionando:

![Screenshot Quarkus Deployed](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/reload-configmap/assets/quarkus-deployed.png "Screenshot Quarkus Deployed")

### Comprobar la respuesta del endpoint y reemplazo en caliente
Al ser el servicio de tipo `NodePort`, mapeando el puerto TCP `8080` hacia el exterior por el `30124`, se podrá acceder al servicio de Quarkus directamente. En este caso se utiliza Windows, y Minikube está funcionando en una VM de virtualbox con ip 192.168.99.132, por lo que al endpoint `/fubar` se accederá mediante la url **http://192.168.99.132:30124/fubar**.
Dado que Quarkus está realizando una lectura correcta de los valores de las propiedades en el configmap, se han sobrescrito los valores originales del archivo `application.properties`, siendo la respuesta del endpoint:
```
foo: foo1 and bar: bar1
```

**Quarkus** es con el único de los tres frameworks con el que no se ha podido alcanzar el reemplazo “en caliente” de actualizaciones en el configmap, por lo que habría que reiniciar el pod para que se viesen reflejados los cambios del configmap en el endpoint.

### Explorar otras posibilidades de la dependencia Kubernetes
La extensión de Kubernetes para Quarkus ofrece otras opciones bastante interesantes:

-	Definir un registro docker desde un property:
     - `quarkus.container-image.registry=http://my.docker-registry.net`
-	Lectura de Secrets indicados en un property de igual forma que los configmaps.
-	Montar volúmenes desde un property:
     - `quarkus.kubernetes.mounts.my-volume.path=/where/to/mount`
-	Cambiar el número de replicas desde un property
     - `quarkus.kubernetes.replicas=3`
-	Tunear cualquier valor de los recursos generados en kubernetes (service account, pull policy, sidecars…) desde properties.
-	Soporte para openshift y knative.
-	Deployment directo a Kubernetes (lanzará además el build previamente a docker, JIB o s2i)
     - `./mvnw clean package -Dquarkus.kubernetes.deploy=true`

Por tanto, ofrece un soporte bastante completo. Una pena el reemplazo en caliente de los valores del configmap.

## Micronaut
### Generar el proyecto
En segundo lugar se analizará de forma similar el framework Micronaut (actualmente en su versión 2.0). Como en el caso anterior, en su web https://micronaut.io/launch/ se deberán seleccionar los “features” que se quieren añadir al proyecto generado. Para este ejemplo solo se seleccionará kubernetes.
```xml
<dependency>
    <groupId>io.micronaut.kubernetes</groupId>
    <artifactId>micronaut-kubernetes-discovery-client</artifactId>
    <version>2.0.0</version>
</dependency>
```

De forma muy similar a Quarkus, el generador ha añadido las dependencias al pom.xml, proporcionando un proyecto base muy sencillo pero funcional. Además, tal y como explican en su guía para su exensión kubernetes (https://micronaut-projects.github.io/micronaut-kubernetes/2.0.0/guide/index.html) ha añadido el archivo `bootstrap.yaml` junto con `application.yaml` en la ruta `src/main/resources`, el cuál será necesario para configurar el acceso a kubernetes:

> “Configuration parsing happens in the bootstrap phase. Therefore, to enable distributed configuration clients, define the following in bootstrap.yml”

Bootstrap viene con el cliente kubernetes activado, y de paso se va a incluir el configmap al que debe acceder:
```yaml
micronaut:
  config-client:
    enabled: true
kubernetes:
  client:
    namespace: default
    config-maps:
      includes:
        - reload-configmap
```

De igual forma al ejemplo anterior con Quarkus, se añade un endpoint que retorna el valor de las propiedades:

```java
@Controller("/fubar")
public class PropertiesResource {

    @Inject
    PropertiesFubar fubar;

    @Get(uri = "/", produces = MediaType.TEXT_PLAIN) 
    HttpResponse<String> getFubar() { 
        return HttpResponse.ok("foo: " + fubar.getFoo() + " bar: " + fubar.getBar());
    }

}

@ConfigurationProperties("properties.fubar")
public class PropertiesFubar {
    private String foo;
    private String bar; 
}
```
### Crear recursos de kubernetes y desplegar
Ahora se añade un configmap que sobrescribirá el valor de las variables del archivo `application.yaml`:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: reload-configmap
data:
  application.yml: |-
    properties:
      fubar:
        bar: bar1
        foo: foo1
```

El archivo de configuración del role para lectura de la api de kubernetes y deployment serán idénticos al ejemplo de Quarkus. Ya se podrá desplegar en minikube tras empaquetar y construir la imagen docker:
```
$ mvn package
$ docker build -t reload-configmap:micronaut .
$ kubectl apply –f ./k8s
```
Se puede observar en el dashboard de minikube como hay un pod de Micronaut funcionando:
 ![Screenshot Micronaut Deployed](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/reload-configmap/assets/micronaut-deployed.png "Screenshot Micronaut Deployed")

### Comprobar la respuesta del endpoint y reemplazo en caliente
Al igual que antes, pero con el puerto indicado en el parámetro NodePort del servicio definido en el archivo `micronaut/k8s/deployment.yaml`, en la url **http://192.168.99.132:30125/fubar** se retornarán los valores de las propiedades sobrescritas por el configmap:
```
foo: foo1 bar: bar1
```

Lo interesante es que ahora, si se cambia el valor de las propiedades en el configmap, se verán reflejados los cambios instantáneamente en el endpoint:
> “By default, this configuration module will watch for ConfigMaps added/modified/deleted, and provided that the changes match with the above filters, they will be propagated to the Environment and refresh it. This means that those changes will be immediately available in your application without a restart.”

### Explorar otras posibilidades de la dependencia Kubernetes
Adicionalmente, el módulo kubernetes de Micronaut ofrece algunas otras características interesantes:
-	Service Discovery de servicios kubernetes (y posible filtrado)
     - `@Client("my-service")`
-	Acceso a secrets de igual forma que configmaps
-	Acceso a la API de kubernetes a “bajo nivel”
     - `private final KubernetesClient client;`
-	Logging y Debugging de las llamadas java y http que se realizan a la api de kubernetes
    -	`<logger name="io.micronaut.http.client" level="TRACE"/>`
    -	`<logger name="io.micronaut.kubernetes" level="TRACE"/>`


## Spring Boot
### Generar el proyecto
Por último, Spring Boot (en su versión 2.3.1) también ofrece una solución completa mediante el modulo **Spring Cloud Kubernetes**. Toda la información relacionada se puede encontrar en su [documentación oficial](https://spring.io/projects/spring-cloud-kubernetes). Una vez en el generador de código de su web https://start.spring.io/, se configura un proyecto maven, con java `11`, y la dependencia `spring-cloud-starter-kubernetes-config`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-config</artifactId>
    <version>1.1.5.RELEASE</version>
</dependency>
```

A continuación, de manera muy similar a Micronaut, la documentación sugiere la configuración a llevar a cabo en el archivo `src/main/resources/bootstrap.yml` (de nuevo debido a que la configuración con kubernetes se ejecutará antes siquiera del deployment):
```yaml
spring:
  cloud:
    kubernetes:
      config:
        enabled: true
        sources:
          - namespace: default
            name: reload-configmap
      reload:
        enabled: true
        mode: event
        strategy: refresh

management:
  endpoint:
    restart:
      enabled: true
```

Por un lado, la configuración para kubernetes activada y enlazando al configmap que se quiere utilizar, y por otro lado, activado el `reload` cada vez que un evento de actualización del configmap ocurra.

De nuevo un endpoint que retorne los valores de las propiedades como en los ejemplos anteriores:

```java
@RestController
@RequestMapping("/fubar")
public class PropertiesResource {

	@Autowired
	PropertiesFubar fubar;

    @GetMapping("/")
    public ResponseEntity<ResponseData> getData() {
        ResponseData responseData = new ResponseData();
        responseData.setFoo(fubar.getFoo());
        responseData.setBar(fubar.getBar());
        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

    @Getter
    @Setter
    public class ResponseData {
        private String foo;
        private String bar;
    }
}

@Configuration
@ConfigurationProperties(prefix = "properties.fubar")
@Getter
@Setter
public class PropertiesFubar {
	private String foo;
	private String bar;
}
```

### Crear recursos de kubernetes y desplegar
Adicionalmente se crea el configmap:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: reload-configmap
data:
  application.yml: |-
    properties:
      fubar:
        bar: bar1
        foo: foo1
```

El archivo de configuración del role para lectura de la api de kubernetes y deployment serán idénticos a los casos anteriores. Ya se podrá desplegar en minikube tras empaquetar y construir la imagen docker:
```
$ mvn package
$ docker build -t reload-configmap:springboot .
$ kubectl apply –f ./k8s
```

Se puede observar en el dashboard de minikube como hay un pod de Springboot funcionando:
 ![Screenshot Spring Boot Deployed](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/reload-configmap/assets/spring-boot-deployed.png "Screenshot Spring Boot Deployed")

### Comprobar la respuesta del endpoint y reemplazo en caliente
Al igual que antes, pero con el puerto indicado en el parámetro NodePort del servicio definido en el archivo `springboot/k8s/deployment.yaml`, en la url **http://192.168.99.132:30123/fubar** se retornarán los valores de las propiedades sobrescritas por el configmap:
```
foo: foo1 bar: bar1
```

Al igual que en Micronaut, si se cambia el valor de las propiedades en el configmap, se verán reflejados los cambios instantáneamente en el endpoint. Pero en este caso, no es una propiedad por defecto del framework, si no que se debe añadir al archivo `bootstrap.yaml` mencionado anteriormente el siguiente parámetro:

```yaml
  reload:
    enabled: true
    mode: event
    strategy: refresh
```

Tal y como se puede leer en la documentación oficial de spring cloud kubernetes:
> “Some applications may need to detect changes on external property sources and update their internal status to reflect the new configuration. The reload feature of Spring Cloud Kubernetes is able to trigger an application reload when a related ConfigMap or Secret changes.”

### Explorar otras posibilidades de la dependencia Kubernetes
Adicionalmente, el Spring Cloud Kubernetes también ofrece otras posibilidades para consumir servicios nativos de Kubernetes:
-	Service Discovery y Discovery Client de servicios kubernetes (y posible filtrado). Además, existe la posibilidad de implementar un circuit breaker y/o fallback desde el lado del cliente simplemente con anotaciones propias del framework.
-	Acceso a secrets de igual forma que configmaps, así como reload de properties en caliente.
-	`Kubernetes awareness`, lo cual permite, por ejemplo, desplegar el servicio de forma local para desarrollo y troubleshooting, sin la necesidad de tener que desplegarlo en kubernetes para que funcione. Incluso cuenta con `Istio awareness`, para interactuar con las APIs de istio mediante un Profile Istio, para consultar reglas de tráfico o circuit breakers.

Todo el código se encuentra disponible en el repositorio https://github.com/MasterCloudApps-Projects/Java-Kubernetes/tree/master/reload-configmap


