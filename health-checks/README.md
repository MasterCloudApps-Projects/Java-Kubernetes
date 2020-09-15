# Análisis health checks en diferentes frameworks java
A continuación, se va a proceder a analizar qué nos ofrecen actualmente los diferentes frameworks de java orientados a microservicios y cloud para facilitar la implementación de health checks en kubernetes.

## Requisitos
    - Conocimientos básicos de kubernetes
    - Maven
    - Cluster local de kubernetes, por ejemplo Minikube
    - Kubectl
  
## En qué consiste el análisis
Aunque los conceptos principales de kubernetes se darán por conocidos, ya que el objetivo de este proyecto no es explicar cómo funciona, ya que para eso ya hay miles de recursos en internet, aquí va una breve introducción:

> Uno de los conceptos básicos de kubernetes son los health checks. La idea es muy básica, ya que kubernetes sabrá cuándo un pod se encuentra sano basándose en estos “checks”. Por un lado, el *“Liveness probe”* (el pod sigue vivo? debería reiniciarse?), y por otro lado, el *“Readiness probe”* (está el pod en condiciones para recibir tráfico?). Mediante archivos de configuración `yaml`, se indicará a kubernetes dónde se encuentran los endpoints a los que debe llamar dentro de la aplicación para realizar los mencionados chequeos. 

Los frameworks java en los que se va a centrar el análisis son **Quarkus**, **Micronaut** y **Springboot**, orientados a cloud y microservicios, y ver qué ofrecen para facilitar la vida a la hora de implementar health checks en una aplicación.

Los tres frameworks se pueden ejecutar con **Maven**, por lo que se podrá compilar cualquiera de ellos con el comando `mvn package` para generar los correspondientes jar. Algo que tienen también en común es que todos ellos cuentan con una web muy similar donde se pueden generar los correspondientes boilerplates, seleccionando la paquetización y las dependencias con las que el proyecto se quiere inicializar:
-	Quarkus: https://code.quarkus.io/
-	Micronaut: https://micronaut.io/launch/
-	Springboot: https://start.spring.io/

Una vez se descargan los proyectos de las correspondientes webs, y teniendo maven instalado, ya serían proyectos funcionales con las dependencias seleccionadas que se podrían ejecutar. Además, todos ellos cuentan con modo desarrollo permitiendo “hot swap” (y funciona muy bien en los tres) para tener un entorno de desarrollo ágil y rápido:
-	Quarkus: `./mvnw compile quarkus:dev`
-	Micronaut: `./mvnw mn:run`
-	Springboot: el modo desarrollo requerirá incluirá la dependencia DevTools y además arrancar la aplicación desde un IDE con plugin SpringBoot (Eclipse, IntelliJ, VS Code..).

El análisis será idéntico para cada uno de los frameworks:
1. Generar el proyecto boilerplate desde la web, añadiendo las dependencias necesarias tanto para tener disponibles los health checks como un datasource para la conexión a una base de datos postgres. La idea es que el simple hecho de tener un datasource activo, ya debería de impactar en los health checks de los diferentes frameworks sin tener que configurar nada adicionalmente, ya que si la conexión a base de datos no funciona, el pod no debería de recibir tráfico alguno desde kubernetes.
2. Añadir yamls básicos tanto de *“deployment”* como de *“service”* para el despliegue en el clúster de kubernetes local (usaré minikube para probar los ejemplos).
3. Desplegar el servicio y analizar la respuesta del endpoint “/health” que incluye la configuración “out of the box”, simplemente añadiendo el datasource de postgres y desplegando en minikube. En principio todos los boilerplate vienen con un Dockerfile o similar incluido.
4. Por último, implementar la interfaz de los health checks de cada framework con el fin de poder crear uno totalmente customizado, simulando la comprobación de un servicio customizado que pudiésemos tener en nuestro negocio. Se desplegará en minikube el código con el nuevo check y se analizará cómo impacta en el endpoint “/health”.

De nuevo resaltar que el entorno de desarrollo ya cuenta con una instalación de minikube en la que se ha desplegado una base de datos Postgres (se incluyen los yaml para desplegarlo, así como para crear un volumen en el directorio del repo `./k8s`):
-	Instalar minikube
-	minikube start
-	kubectl apply –f ./k8s/
-	minikube dashboard
![Minikube initial dashboard](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/health-checks/assets/minikube-initial-dashboard.png "Minikube initial dashboard")


## Quarkus
### Generar el proyecto 
En primer lugar se analiza Quarkus (actualmente en su versión 1.6). Tras ver en su documentación que implementan la especificación de MicroProfile Health a través de la extensión “SmallRye Health”, se va a generar un boilerplate desde su web https://code.quarkus.io/ y añadir simplemente la extensión mencionada en la sección Cloud. Además se necesitará el Driver de Postgres y Hibernate para configurar el datasource:
![Screenshot Quarkus generator](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/health-checks/assets/quarkus-initializr.png "Screenshot Quarkus generator")
 

Al seleccionar las extensiones, simplemente ha añadido las dependencias al **pom.xml**:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
```

Tras la descarga, además, te apuntan al enlace https://quarkus.io/guides/microprofile-health donde se puede encontrar una guía sobre cómo implementarlo.

La única modificación que se realizará por ahora en el código será configurar los datos de conexión a postgres en el archivo `src/main/resources/application.properties`:

```
quarkus.datasource.driver=org.postgresql.Driver
quarkus.datasource.url=${JDBC_DATABASE_URL:jdbc:postgresql://localhost:5432/postgres}
quarkus.datasource.username=${JDBC_DATABASE_USERNAME:postgres}
quarkus.datasource.password=${JDBC_DATABASE_PASSWORD:password}
```

Como apunte importante, se quieren configurar con variables de entorno y valores por defecto como se puede apreciar, ya que los valores por defecto se utilizarán para un arranque local y las variables de entorno cuando se ejecute bajo kubernetes y los datos de conexión se configuren desde un configmap.

### Añadir recursos de kubernetes
A continuación, se añade un `yaml` (se puede encontrar en el directorio `quarkus/k8s/health-check.yaml` del repositorio) para despliegue en kubernetes conteniendo los recursos **deployment** y **service**, el cuál será casi idéntico para las tres aplicaciones (simplemente se pasarán las variables de entorno con diferente nombre). Especial atención a la configuración de las probes, sin esta configuración kubernetes no eliminaría el pod cuando el endpoint de liveness no funcionase ni dejaría de redirigir tráfico al mismo cuando el endpoint de readiness no funcionase:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: health-check-quarkus
  name: health-check-quarkus
spec:
  replicas: 1
  selector:
    matchLabels:
      app: health-check-quarkus
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: health-check-quarkus
    spec:
      securityContext:
         runAsUser: 1000300
      containers:
      - env:
        - name: QUARKUS_DATASOURCE_PASSWORD
          value: password
        - name: QUARKUS_DATASOURCE_URL
          value: jdbc:postgresql://postgresdb:5432/postgres
        - name: QUARKUS_DATASOURCE_USERNAME
          value: postgres
        image: health-check:quarkus
        imagePullPolicy: Never
        name: health-check-quarkus
        securityContext:
          allowPrivilegeEscalation: false
        livenessProbe:
          httpGet:
            path: /actuator/health/live
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 3
---
apiVersion: v1
kind: Service
metadata:
  labels:
    app: health-check-quarkus
  name: health-check-quarkus
spec:
  ports:
  - protocol: TCP
    port: 8080
    nodePort: 30799
  selector:
    app: health-check-quarkus
  type: NodePort
```

### Desplegar y analizar respuesta del endpoint /health
Ya se podría arrancar la aplicación con el código tal y como se ha descargado desde el generador de código, es decir, sin ninguna clase más que un endpoint con un hello world y el pom.xml:
```
$ ./mvnw compile quarkus:dev
```

Pero en este caso directamente se buildeará el proyecto con maven, la imagen docker y se desplegará en minikube:
```
$ cd quarkus
$ mvn package
$ eval $(minikube docker-env)
$ docker build -t health-check:quarkus .
$ kubectl apply –f ./k8s/health-check.yaml
```
Ya se puede observar en el dashboard de minikube como hay un pod de quarkus funcionando:
![Screenshot Quarkus deployed in minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/health-checks/assets/minikube-dashboard-quarkus.png "Screenshot Quarkus deployed in minikube")
 

Al ser el servicio de tipo **NodePort**, utilizando el puerto TCP al que se ha indicado que mapee el puerto 8080 hacia el exterior, el 30799, se podrá acceder al servicio de Quarkus directamente. En este caso se utiliza Windows y Minikube está funcionando en una VM de virtualbox con ip 192.168.99.132, por lo que al endpoint de health se accederá mediante la url **http://192.168.99.132:30799/health**

Dado que no se ha implementado nada, todo está por defecto, habrá simplemente un **Readiness probe** `/health/ready` que comprueba la conexión a base de datos, con una respuesta en formato `JSON`:

**HTTP 200 OK**
```json
{
    "status": "UP",
    "checks": [
        {
            "name": "Database connections health check",
            "status": "UP"
        }
    ]
}
```

Estado `UP`, sano, y simplemente con un check adicional ya que existe un datasource configurado. Si fallase la conexión a base de datos, la respuesta será `HTTP 503 SERVICE UNAVAILABLE` con status `DOWN`. En este caso, fallaría el **Readiness probe** y el pod no recibiría tráfico por parte de k8s.
Y un **Liveness Probe** `/health/live`, idéntico, pero sin el check a base de datos, ya que es correcto que el pod no reciba tráfico si la base de datos no funciona, pero no debería ser eliminado/reiniciado, ya que no solucionaría nada.

### Implementar health check customizado
Normalmente, además de comprobar la conexión a base de datos, los health checks involucran la comprobación del estado de algún servicio de negocio. Para ello, si se sigue la guía a la que apuntaba el generador de código de Quarkus, se debe hacer un override del método `HealthCheckResponse()` de la clase `import org.eclipse.microprofile.health.HealthCheck` para controlar la respuesta de la Readiness Probe de manera que se pueda comprobar, adicionalmente al estado de la base de datos, un servicio custom como check adicional muy fácilmente (`src/main/java/com/javieraviles/health/CustomHealthCheck.java`):

```java
@Readiness
@ApplicationScoped
public class CustomHealthCheck implements HealthCheck {

	private boolean isCustomServiceUp = true;
	private String serviceName = "Custom-service";

	@Override
	public HealthCheckResponse call() {

		HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named(serviceName);

		try {
			simulateCustomServiceConnectionVerification();
			responseBuilder.up();
		} catch (IllegalStateException e) {
			responseBuilder.down();
		}

		return responseBuilder.build();
	}

	private void simulateCustomServiceConnectionVerification() {
		if (!isCustomServiceUp) {
			throw new IllegalStateException("Cannot reach custom service");
		}
	} 
}
```

Suponiendo que el booleano `isCustomServiceUp` comprobase realmente que el custom service funciona, se recibe un 200 OK en el **Readiness Probe** `http://192.168.99.132:30799/health/ready`cuando estén tanto la conexión a base de datos como al servicio custom funcionando:


**HTTP 200 OK**
```json
{
    "status": "UP",
    "checks": [
        {
            "name": "Custom-service",
            "status": "UP"
        },
        {
            "name": "Database connections health check",
            "status": "UP"
        }
    ]
}
```

Y un `Service Unavailable` cuando alguno de los dos no funcione, por ejemplo cambiando el valor del booleano `isCustomServiceUp` a false y desplegando de nuevo:

**HTTP 503 SERVICE UNAVAILABLE**
```json
{
    "status": "DOWN",
    "checks": [
        {
            "name": "Custom-service",
            "status": "DOWN"
        },
        {
            "name": "Database connections health check",
            "status": "UP"
        }
    ]
}
```


Si por el contrario fuese la base de datos la que fallase, se puede borrar y generar el mismo escenario:

```
$ cd /k8s && kubectl delete –f ./postgresdb.yaml
```

En cuanto la base de datos esté de nuevo disponible, el serivicio volverá a recibir tráfico con normalidad.

## Micronaut
### Generar el proyecto 
En segundo lugar se va a analizar qué ofrece **Micronaut** (actualmente en su versión 2.0) para facilitar la implementación de los health check. De manera similar al caso anterior, en su web https://micronaut.io/launch/ se puede crear un boilerplate donde se seleccionarán los **features** que se quieren añadir al proyecto de base. Tras leer un poco su documentación, se necesitarán los features **management** (añadirá el soporte para monitorear la aplicación mediante endpoints), **data-jpa** (para configurar el datasource) y **postgres** (DB drivers):

![Screenshot Micronaut Initializr](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/health-checks/assets/micronaut-initializr.png "Screenshot Micronaut Initializr")

De manera muy similar a Quarkus, el generador simplemente ha añadido las dependencias al pom.xml, proporcionando un proyecto base muy sencillo pero funcional.

Para configurar la conexión a **Postgres**, será tan sencillo como añadir un datasource a la configuración de la aplicación en el archivo `src/main/resources/application.yml`:
```yaml
datasources:
  default:
    url: ${JDBC_URL:`jdbc:postgresql://localhost:5432/postgres`}
    driverClassName: org.postgresql.Driver
    username: ${JDBC_USER:postgres}
    password: ${JDBC_PASSWORD:password}
    schema-generate: CREATE_DROP
    dialect: POSTGRES
```

Aquí también se crean tanto variables de entorno para pasar los valores de conexión una vez en kubernetes, pero con valores por defecto en caso de desarrollo en local. Además, de igual forma que en el ejemplo anterior con Quarkus, se añade un `yaml` básico con los recursos **deployment** y **service**, con el fin de poder ser desplegado en k8s `micronaut/k8s/health-check.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: health-check-micronaut
  name: health-check-micronaut
spec:
  replicas: 1
  selector:
    matchLabels:
      app: health-check-micronaut
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: health-check-micronaut
    spec:
      securityContext:
         runAsUser: 1000300
      containers:
      - env:
        - name: JDBC_PASSWORD
          value: password
        - name: JDBC_URL
          value: jdbc:postgresql://postgresdb:5432/postgres
        - name: JDBC_USER
          value: postgres
        image: health-check:micronaut
        imagePullPolicy: Never
        name: health-check-micronaut
        securityContext:
          allowPrivilegeEscalation: false
        livenessProbe:
          httpGet:
            path: /info
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 3
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 3
---
apiVersion: v1
kind: Service
metadata:
  labels:
    app: health-check-micronaut
  name: health-check-micronaut
spec:
  ports:
  - protocol: TCP
    port: 8080
    nodePort: 31270
  selector:
    app: health-check-micronaut
  type: NodePort
```

### Desplegar y analizar respuesta del endpoint /health
Como en el ejemplo anterior, se buildeará el proyecto con maven, la imagen docker y se desplegará en minikube:
```
$ cd micronaut
$ mvn package
$ eval $(minikube docker-env)
$ docker build -t health-check:micronaut .
$ kubectl apply –f ./k8s/health-check.yaml
```
Ya se puede observar en el dashboard de minikube como hay un pod de Micronaut funcionando junto al de Quarkus:
![Screenshot Micronaut deployed in minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/health-checks/assets/minikube-dashboard-micronaut.png "Screenshot Micronaut deployed in minikube")
 

Al ser el servicio de tipo **NodePort**, utilizando el puerto TCP al que se ha indicado que mapee el puerto 8080 hacia el exterior, el 31270, se podrá acceder al servicio de Micronaut directamente. Esta vez solo estará disponible un único endpoint **http://192.168.99.132:31270/health** que comprueba la conexión a base de datos tal y como describen en su documentación:

> “If we would have one or more DataSource beans for database access in our application context a health indicator is added as well to show if the database(s) are available or not.”

Con una respuesta en formato `JSON`:

**HTTP 200 OK**
```json
{
   "name":"healthcheck",
   "status":"UP",
   "details":{
      "jdbc":{
         "name":"healthcheck",
         "status":"UP",
         "details":{
            "jdbc:postgresql://postgresdb:5432/postgres":{
               "name":"healthcheck",
               "status":"UP",
               "details":{
                  "database":"PostgreSQL",
                  "version":"12.3 (Debian 12.3-1.pgdg100+1)"
               }
            }
         }
      }
   }
}
```

Estado `UP`, sano, y simplemente con un check adicional ya que existe un datasource configurado. Si fallase la conexión a base de datos, la respuesta sería `HTTP 503 SERVICE UNAVAILABLE` con status `DOWN`.

### Implementar health check customizado
Como en el caso anterior, además de comprobar la conexión a base de datos, los health checks involucran la comprobación del estado de algún servicio de negocio. Siguiendo la documentación oficial de **Micronaut**, nos indica que se debe hacer un override del método `getResult()` de la clase [HealthIndicator](https://docs.micronaut.io/latest/api/io/micronaut/management/health/indicator/HealthIndicator.html) en una clase (`src/main/java/com/javieraviles/health/CustomHealthCheck.java`):

```java
@Singleton
public class CustomHealthCheck implements HealthIndicator {

	private boolean isCustomServiceUp = true;
	private String serviceName = "Custom-service";

	@Override
	public Publisher<HealthResult> getResult() {

		HealthResult.Builder builder = HealthResult.builder(serviceName);

		try {
			simulateCustomServiceConnectionVerification();
			builder.status(HealthStatus.UP);
		} catch (IllegalStateException e) {
			builder.status(HealthStatus.DOWN);
		}

		return Publishers.just(builder.build());
	}

	private void simulateCustomServiceConnectionVerification() {
		if (!isCustomServiceUp) {
			throw new IllegalStateException("Cannot reach custom service");
		}
	}

}
```

Ahora el endpoint **http://192.168.99.132:31270/health** tendrá en cuenta tanto la conexión a base de datos como el estado del servicio custom:

**HTTP 200 OK**
```json
{
   "name":"healthcheck",
   "status":"UP",
   "details":{
      "Custom-service":{
         "name":"healthcheck",
         "status":"UP"
      },
      "jdbc":{
         "name":"healthcheck",
         "status":"UP",
         "details":{
            "jdbc:postgresql://postgresdb:5432/postgres":{
               "name":"healthcheck",
               "status":"UP",
               "details":{
                  "database":"PostgreSQL",
                  "version":"12.3 (Debian 12.3-1.pgdg100+1)"
               }
            }
         }
      }
   }
}
```

De igual forma que en el caso anterior, si se cambia el valor del booleano `isCustomServiceUp` a false o se borra la base de datos, se generaría un escenario de fallo de la readiness probe:

```
$ cd /k8s && kubectl delete –f ./postgresdb.yaml
```

En cuanto la base de datos esté de nuevo disponible, el serivicio volverá a recibir tráfico con normalidad.

## Spring Boot
### Generar el proyecto 
Por último, **Spring Boot** (en su versión 2.3.1) también ofrece una solución completa mediante el módulo Actuator. Podemos encontrarlo en su documentación oficial como Kubernetes Probes.

Una vez en el generador de código en su web **https://start.spring.io/**, se configura un proyecto **maven**, con **java 11**, y las dependencias **Actuator**, **DevTools** (para tener hotswap como en los casos anteriores), **Spring Data JPA** y el driver de **Postgres**:

![Screenshot SpringBoot Initializr](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/health-checks/assets/springboot-initializr.png "Screenshot SpringBoot Initializr")

Todas las configuraciones que se van a aplicar, tanto para activar las probes como para configurar en el datasource, se realizan en el archivo `spring-boot/src/main/java/resources/application.properties`:
```
management.health.probes.enabled=true
management.endpoint.health.show-details=ALWAYS
spring.jpa.hibernate.ddl-auto=update
## PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=password
```

De igual forma que en los ejemplos anteriores, se añade un `yaml` básico con los recursos **deployment** y **service**, con el fin de poder ser desplegado en k8s `spring-boot/k8s/health-check.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: health-check-springboot
  name: health-check-springboot
spec:
  replicas: 1
  selector:
    matchLabels:
      app: health-check-springboot
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: health-check-springboot
    spec:
      securityContext:
         runAsUser: 1000300
      containers:
      - env:
        - name: SPRING_DATASOURCE_PASSWORD
          value: password
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgresdb:5432/postgres
        - name: SPRING_DATASOURCE_USERNAME
          value: postgres
        image: health-check:spring-boot
        imagePullPolicy: Never
        name: health-check-springboot
        securityContext:
          allowPrivilegeEscalation: false
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 3
---
apiVersion: v1
kind: Service
metadata:
  labels:
    app: health-check-springboot
  name: health-check-springboot
spec:
  ports:
  - protocol: TCP
    port: 8080
    nodePort: 30033
  selector:
    app: health-check-springboot
  type: NodePort
```

### Desplegar y analizar respuesta del endpoint /health
Y como en los ejemplos anteriores, se buildeará el proyecto, la imagen docker y se desplegará en minikube:
```
$ cd spring-boot
$ mvn package
$ eval $(minikube docker-env)
$ docker build -t health-check:spring-boot .
$ kubectl apply –f ./k8s/health-check.yaml
```
Ya se puede observar en el dashboard de minikube como hay un pod de SpringBoot funcionando junto a los de Quarkus y Micronaut:
 ![Screenshot SpringBoot deployed in minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/health-checks/assets/minikube-dashboard-springboot.png "Screenshot SpringBoot deployed in minikube")
 
Al ser el servicio de tipo **NodePort**, utilizando el puerto TCP al que se ha indicado que mapee el puerto 8080 hacia el exterior, el 30033, se podrá acceder al servicio de SpringBoot directamente. El endpoint **http://192.168.99.132:30033/actuator/health** comprobará la conexión a base de datos por defecto también:

Con una respuesta en formato `JSON`:

**HTTP 200 OK**
```json
{
   "status":"UP",
   "components":{
      "db":{
         "status":"UP",
         "details":{
            "database":"PostgreSQL",
            "validationQuery":"isValid()"
         }
      },
      "livenessState":{
         "status":"UP"
      },
      "ping":{
         "status":"UP"
      },
      "readinessState":{
         "status":"UP"
      }
   },
   "groups":[
      "liveness",
      "readiness"
   ]
}
```

### Implementar health check customizado
Si adicionalmente se implementa el custom check como en los ejemplos anteriores, siguiendo la documentación oficial de Springboot, nos indica que se debe hacer un override del método `health()` de la interfaz [HealthIndicator](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/actuate/health/HealthIndicator.html) de la siguiente forma:

```java
@Component
public class CustomHealthCheck implements HealthIndicator {

	private boolean isCustomServiceUp = true;
	private String serviceName = "Custom-service";

	@Override
	public Health health() {
		try {
			simulateCustomServiceConnectionVerification();
			return Health.up().withDetail(serviceName, "Available").build();
		} catch (IllegalStateException e) {
			return Health.down().withDetail(serviceName, e.getMessage()).build();
		}
	}

	private void simulateCustomServiceConnectionVerification() {
		if (!isCustomServiceUp) {
			throw new IllegalStateException("Cannot reach custom service");
		}
	}

}
```

Ahora el endpoint **http://192.168.99.132:30033/health** tendrá en cuenta tanto la conexión a base de datos como el estado del servicio custom, y la indisponibilidad de cualquiera de los dos haría que el health check total sea status `DOWN` con código `HTTP 503 Service Unavailable`:

```json
{
   "status":"UP",
   "components":{
      "customHealthCheck":{
         "status":"UP",
         "details":{
            "Custom-service":"Available"
         }
      },
      "db":{
         "status":"UP",
         "details":{
            "database":"PostgreSQL",
            "validationQuery":"isValid()"
         }
      },
      "livenessState":{
         "status":"UP"
      },
      "ping":{
         "status":"UP"
      },
      "readinessState":{
         "status":"UP"
      }
   },
   "groups":[
      "liveness",
      "readiness"
   ]
}
```

Y de nuevo una forma de comprobar que todos los readiness probes están funcionando correctamente, es eliminando el pod de postgres:

```
$ cd /k8s && kubectl delete –f ./postgresdb.yaml
```

Se podrá observar que los tres services (quarkus, micronaut y spring-boot) responderán con un `503`, ya que cada uno de sus respectivos pods, aunque se puedan seguir visualizando como pods “vivos” en el dashboard de minikube, están fallando los readiness probes y por tanto, k8s no les dirigirá tráfico y el servicio responderá con 503 service unavailable. En el instante en el que la base de datos vuelva a estar disponible, todos los pods funcionarán con normalidad.


Todo el código se encuentra disponible en el repositorio https://github.com/MasterCloudApps-Projects/Java-Kubernetes/tree/master/health-checks
