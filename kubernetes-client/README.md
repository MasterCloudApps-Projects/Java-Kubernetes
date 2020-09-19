# Diseño de un operador de Kubernetes en Java
En Kubernetes, el concepto de operador es similar al de un controlador, el cuál observa en bucle el estado del clúster y realiza cambios cuando es necesario (debe llevar el estado actual al estado deseado). El más conocido es probablemente el deployment controller; cada vez que se realiza un nuevo despliegue, se puede observar cómo este crea un pod `deploy` que se encarga de crear tantas réplicas de la aplicación como la especificación indique. Estos controladores se ejecutan en el `control plane` de Kubernetes, pero uno customizado como el que se quiere diseñar puede ser ejecutado en cualquier sitio.

Por tanto, **un operador será un controlador**, pero especializado **con conocimiento específico de negocio**, interactuando con el clúster de kubernetes para crear, configurar y gestionar instancias de la aplicación, un recurso específico. Esta manera de interactuar del operador con el clúster de kubernetes será a través de la Kubernetes API Server, el frontend del clúster a través del cual se podrán validar y configurar objetos como pods, services y replicationcontrollers entre otros.

Por ejemplo, la conocida `kubectl` es una herramienta de línea de comando para interactuar exactamente con esta API. Solo que en este caso se quiere interactuar con la API vía HTTP y desde un pod del propio kubernetes.

  - [Requisitos](#requisitos)
  - [Ideas principales sobre el diseño](#ideas-principales-sobre-el-diseño)
    - [Autorización](#autorización)
    - [Cliente java io.kubernetes](#cliente-java-iokubernetes)
    - [Cliente java io.fabric8](#cliente-java-iofabric8)
  - [Creación y despliegue del operador java](#creación-y-despliegue-del-operador-java)

## Requisitos
    - Conocimientos básicos de kubernetes
    - Maven
    - Cluster local de kubernetes, por ejemplo Minikube
    - Kubectl

## Ideas principales sobre el diseño
### Autorización
La primera condición, por tanto, a la hora de crear un operador de kubernetes, es permitir la comunicación del contenedor con la API. Para ello, se necesitará crear un `yaml` de autorización del tipo:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: operator-example
rules:
  - apiGroups:
      - ""
    resources:
      - pods
    verbs:
      - list
      - watch
      - create
      - delete
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: operator-service
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: operator-example
subjects:
  - kind: ServiceAccount
    name: operator-service
roleRef:
  kind: ClusterRole
  name: operator-example
  apiGroup: rbac.authorization.k8s.io
```

El cual creará una `service account` con un rol con permisos suficientes sobre las acciones que se quieren llevar a cabo a través de la API. Tras crear el archivo yml `./k8s/authorization.yml`, bastará con el siguiente comando una vez la instancia de kubernetes esté funcionando:

```
$ minikube start
$ kubectl apply –f ./authorization.yml
```

### Cliente java io.kubernetes
Al querer desarrollar el operador en Java, se puede encontrar en el propio perfil de github de Kubernetes un [cliente java](https://github.com/kubernetes-client/java). Los hay para casi todos los lenguajes, al fin y al cabo es un cliente generado a partir de la especificación de swagger. Esto facilitará mucho las cosas para tratar con la API server de kubernetes. En un nuevo proyecto maven, se añadiría la dependencia:

```xml
<dependency>
      <groupId>io.kubernetes</groupId>
      <artifactId>client-java</artifactId>
</dependency>
```

Tal y como se ha mencionado anteriormente, el concepto básico del operador es un bucle que observe el estado del clúster. Y ese es precisamente el ejemplo base que ofrecen en el `README` del repositorio:

```java
public class WatchExample {
    public static void main(String[] args) throws IOException, ApiException{
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        Watch<V1Namespace> watch = Watch.createWatch(
                client,
                api.listNamespaceCall(null, null, null, null, null, 5, null, null, Boolean.TRUE, null, null),
                new TypeToken<Watch.Response<V1Namespace>>(){}.getType());

        for (Watch.Response<V1Namespace> item : watch) {
            System.out.printf("%s : %s%n", item.type, item.object.getMetadata().getName());
        }
    }
}
```

El cual se mantendrá observando todos los objetos que encuentre e imprimirá por pantalla algunos metadatos de cada uno. Y realmente esta es la base del operador. La siguiente idea importante es capturar los eventos que ocurran y realizar las acciones pertinentes según el caso.

Para ello, el cliente de kubernetes cuenta con la interfaz `ResourceEventHandler` (https://javadoc.io/static/io.kubernetes/client-java/6.0.1/io/kubernetes/client/informer/ResourceEventHandler.html), para la cual se deben implementar los métodos `onAdd()`, `onUpdate()`, `onDelete()`. De esta forma, cuando se reciba un evento por ejemplo comunicando que se ha creado un nuevo pod, se ejecutará el método `onAdd()`, recibiendo un objeto pod como argumento.

Aquí es donde se implementaría la lógica para que el operador pueda crear, modificar o eliminar los pods de la aplicación dependiendo del evento recibido.

### Cliente java io.fabric8
Otro cliente kubernetes para java es **fabric8** https://github.com/fabric8io/kubernetes-client. Similar al caso anterior, la dependencia para un proyecto maven sería:

```xml
<dependency>
       <groupId>io.fabric8</groupId>
       <artifactId>kubernetes-client</artifactId>
</dependency>
```

En este caso, Faric8 tiene un uso algo más sencillo y además tiene mucho más apoyo de la comunidad.

Para implementar en este caso el operador, también se podría hacer uso de la interfaz `ResourceEventHandler`, pero en Fabric8, la interfaz `Watcher` (https://www.javadoc.io/doc/io.fabric8/kubernetes-client/3.1.8/io/fabric8/kubernetes/client/Watcher.html) parece más atractiva. Recibe un `Action` y en consecuencia se podrá implementar una lógica u otra con un switch-case por ejemplo. También cuenta con un método `onClose()`.

Además, el concepto de un operador kubernetes en Java tiene más sentido aún gracias a GraalVM, ya que permitirá un tamaño de la imagen de docker de unos 40MB frente a los 200MB si no se hiciese uso de una imagen nativa con GraalVM. Como ventaja adicional, tiene un tiempo de arranque del orden de 0.5 segundos frente a los 10-15 segundos de la JVM.

## Creación y despliegue del operador java
Una vez claro el concepto, las posibilidades, y más o menos los componentes con los que el operador debe contar, merece la pena echar un vistazo a un boilerplate de Nicolas Frankel; se hace precisamente uso de **GraalVM**, **fabric8** y el **patrón sidecar** para la creación de un operador java que gestiona una instancia de Hazelcast (https://github.com/nfrankel/jvm-controller).

Teniendo en cuenta lo mencionado anteriormente y el boilerplate, para este ejemplo se crean dos clases, `src/main/java/com/javieraviles/Sidecar.java` y `src/main/java/com/javieraviles/SidecarWatcher.java`. La primera simplemente consiste en arranca la aplicación e iniciar el bucle de control mediante un `watch` en el namespace indicado:

```java
public static void main(String[] args) {
    DefaultKubernetesClient client = new DefaultKubernetesClient();
    client.pods().inNamespace(SidecarWatcher.NAMESPACE).watch(new SidecarWatcher(client));
}
```

Y en la clase `SidecarWatcher` es donde se realizará la gestión del pod de la aplicación dependiendo de la acción recibida:

```java
public class SidecarWatcher implements Watcher<Pod> {
    @Override
    public void eventReceived(Action action, Pod pod) {
        switch (action) {
            case ADDED:
                // crear pod aplicación
                break;
            case DELETED:
                // borrar pod aplicación
                break;
        }
    }
}
```

Además, en esta clase se puede configurar de manera muy sencilla qué imagen docker contendrá el pod de aplicación. Se utilziará Hazelcast como en el ejemplo de Nicolas Frankel:

```
private static final String SIDECAR_IMAGE_NAME = "hazelcast/hazelcast:3.12.5";
private static final String SIDECAR_POD_NAME = "hazelcast";
```

El resto del boilerplate no es muy relevante en este momento, aunque si necesario, como el `Dockerfile` para crear la imagen nativa de GraalVM `./Dockerfile` o el `yml` para desplegar el operador en kubernetes `./k8s/deploy.yml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: k8soperator
---
apiVersion: v1
kind: Pod
metadata:
  namespace: k8soperator
  name: custom-operator
spec:
  serviceAccountName: operator-service
  containers:
    - name: custom-operator
      image: k8s-operator:1.0
      imagePullPolicy: Never
```

Simplemente un pod, no un deployment.

Por tanto, una vez arrancado el clúster de kubernetes (para el ejemplo se va a utilizar minikube), bastará con compilar el jar, construir la imagen docker y crear los recursos en kubernetes (autorización de acceso a la api de k8s y despliegue del operador):

```
$ mvn package
$ docker build -t k8s-operator:1.0 .
$ kubectl apply –f ./k8s
$ minikube dashboard
```

Y con esto se crearía un pod llamado `custom-operator`, y a su vez este crearía un `hazelcast-pod-operator` a través de la api de kubernetes, un pod que no se ha indicado a kubectl que sea creado explícitamente, y por tanto lo ha creado el propio `custom-operator` desde el container (el sidecar):
 
![Screenshot Custom Operator deployed in minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/kubernetes-client/assets/custom-operator-dashboard.png "Screenshot Custom Operator deployed in minikube")

El operador `custom-operator` mantendrá el pod de hazelcast mientras exista, pero si se borra el pod, también se borrará el sidecar de hazelcast.

Para lograr este comportamiento propio de un operador, en vez de manualmente crear y borrar el sidecar desde el operador dependiendo del evento, en kubernetes existe el concepto de “ownership”. Por lo que el `custom-operator` será declarado como “dueño” del sidecar hazelcast, como se observa en el `SidecarWatcher` cuando se recibe un Action de tipo `ADDED`:

```java
private void createSidecar(Pod pod) {
    String podName = pod.getMetadata().getName();
    String name = SIDECAR_POD_NAME + "-" + podName;
    client.pods().inNamespace(NAMESPACE).createNew()
            .withApiVersion("v1")
            .withKind("Pod")
            .withNewMetadata()
                .withName(name)
                .withNamespace(pod.getMetadata().getNamespace())
                .addNewOwnerReference()
                    .withApiVersion("v1")
                    .withKind("Pod")
                    .withName(podName)
                    .withUid(pod.getMetadata().getUid())
                .endOwnerReference()
                .addToLabels("sidecar", "true")
            .endMetadata()
            .withNewSpec()
                .addNewContainer()
                    .withName(name)
                    .withImage(SIDECAR_IMAGE_NAME)
                .endContainer()
            .endSpec()
            .done();
}
```

Por lo que si se borra el pod `custom-operator`, kubernetes se encargará de borrar el sidecar.

Todo el código se encuentra disponible en el repositorio https://github.com/MasterCloudApps-Projects/Java-Kubernetes/tree/master/kubernetes-client
