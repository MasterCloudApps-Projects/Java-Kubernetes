# Optimización de recursos de la JVM en Kubernetes
Uno de los principales retos al desplegar servicios en kubernetes de manera satisfactoria es **ajustar apropiadamente los recursos**. Concretamente, las aplicaciones java necesitan especial atención ya que la **JVM** se puede volver insaciable en algunos casos, sufriendo muy frecuentemente tanto OOM kills como tiempos de arranque muy lentos.

  - [Requisitos](#requisitos)
  - [En qué consiste el análisis](#en-qué-consiste-el-análisis)
  - [Cómo establecer los recursos del servicio](#cómo-establecer-los-recursos-del-servicio)
  - [Cómo optimizar el uso de memoria de la JVM en contenedores docker](#cómo-optimizar-el-uso-de-memoria-de-la-jvm-en-contenedores-docker)
  - [Troubleshooting de pods java en kubernetes](#troubleshooting-de-pods-java-en-kubernetes)

## Requisitos
    - Conocimientos básicos de kubernetes
    - Conocimientos básicos de la JVM
    - Maven
    - Cluster local de kubernetes, por ejemplo Minikube
    - Kubectl
  
## En qué consiste el análisis
Se analizará el consumo de **memoria** y **CPU** de un microservicio springboot básico (con health check y una conexión a postgres, todo funcionando en kubernetes), tanto en la fase de arranque como de ejecución de la aplicación, con la intención de crear y ajustar ambos parámetros con valores apropiados tanto para `resources.requests` como para `resources.limits`.

Aunque se dan por conocidos estos conceptos de kubernetes, un repaso a qué representan estos valores hará que el análisis se entienda un poco mejor.

> Al pedir a Kubernetes que ejecute una aplicación, el scheduler de kubernetes buscará un nodo en el cluster donde los pods de la aplicación puedan ser ejecutados. El principal factor para determinar si el pod puede ser ejecutado en un nodo será si tiene suficiente memoria y CPU; y aquí es donde la sección **resources** del yaml de kubernetes entra en juego, con dos apartados, **requests y limits**.

> **Requests** representará los valores mínimos que cada pod necesitará para arrancar de manera satisfactoria. En caso de no encontrar un nodo que cumpla dichos requisitos, el pod quedará marcado como `unschedulable`.

> Una vez el pod se encuentre en ejecución, podrá requerir memoria y CPU adicional al nodo; aquí es donde entra en juego el parámetro **limits**, estableciendo el techo máximo tanto de memoria como de CPU para cada pod.

La **motivación** por tanto es clara, proporcionando unos valores adecuados de resources en el deployment de kubernetes, se asegurará la existencia de recursos suficientes en los nodos donde se ejecutan los pods de la aplicación, es decir, se proporcionará al cluster de kubernetes la oportunidad de realizar una **eficiente gestión de los recursos CPU y memoria**.

La aplicación de la que se va a partir será un servicio springboot, con un datasource para postgres y un endpoint, haciendo uso de actuator, para health checks. El yaml de configuración de la aplicación en kubernetes no contiene por el momento la sección resources. Tanto postgres como la aplicación se ejecutarán en un cluster de kubernetes local usando minikube (todos los yaml para crear los recursos en minikube disponibles en el directorio `/k8s`):
```
$ mvn package
$ eval $(minikube docker-env)
$ docker build -t health-check:spring-boot .
$ minikube start
$ kubectl apply –f ./k8s/
$ minikube dashboard
```

Se **medirá** a continuación qué **uso de memoria y CPU** realiza la aplicación para incluir la sección resources en el yaml.


## Cómo establecer los recursos del servicio
Primero se necesitará activar el **addon metrics-server** de minikube para poder saber qué uso de memoria y CPU hace la aplicación en tiempo real:
```
$ minikube addons enable metrics-server
```

En el dashboard de minikube ya se podrá visualizar las métricas de la aplicación en “reposo”:

![Idle springboot metrics in kubernetes](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/resources/assets/idle-metrics.png "Idle springboot metrics in kubernetes")
 

Aproximadamente `5m cores` de CPU (con tendencia a 0 si la aplicación no recibe carga alguna tras el startup) y `160Mb` total de memoria (heap, non-heap, SO…) estable. Es decir, la aplicación necesitará, como poco, estos valores para poder mínimamente funcionar. De aquí, por tanto, se podrán extraer los valores para el parámetro `resources.requests`:
```yaml
resources:
    requests:
      memory: 160Mi
      cpu: 5m
```

A continuación, con la finalidad de establecer los parámetros para `resources.limits`, se ejecutará un **test de carga** (100 req/s, ya que es la carga pico en la que el servico va a trabajar) usando la herramienta javascript `Artillery` para analizar uso de CPU y memoria bajo una carga alta. El script se encuentra directamente en la raíz del proyecto, `load_test.yml`, y será tan sencillo ejecutarlo como:

```
  $ npm install artillery
	$ artillery run ./load_test.yml
```

Como nota importante, al ser un servicio de tipo `NodePort`, utilizando el puerto TCP al que mapea el puerto 8080 hacia el exterior (en este caso el puerto TCP 8080 se mapea al 30270 en el service), se podrá acceder al servicio de la aplicación directamente (sin uso de Ingress). En este caso se utiliza Windows, y minikube está funcionando sobre una VM de virtualbox con ip 192.168.99.132, por lo que la url base del test de carga será **http://192.168.99.132:30270/**.

Una vez se ha lanzado el **test de carga**, conforme pasan los segundos y se estabilizan las peticiones por segundo del test, se puede observar cómo se estabiliza también el **uso de CPU y memoria** de la aplicación bajo alta carga:
 
 ![Load tests springboot metrics in kubernetes](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/resources/assets/load-test-metrics.png "Load tests springboot metrics in kubernetes")

Se puede claramente determinar a partir del gráfico cómo la aplicación llega a tener un pico de casi `0.3 cores` de **CPU** cuando la carga comienza a ser grande y se estabiliza en unos `0.15 cores`; en cuanto a la **memoria**, hay una tendencia al uso de unos `450Mb` como máximo bajo carga mantenida. Con estos datos, podemos hacernos una idea bastante aproximada de qué cifras requiere cada uno de los pods y se establecerá el parámetro `resources.limits.memory`. La razón por la que **no se configura un CPU limit**, es porque podría afectar al tiempo de arranque de la aplicación, por lo que se dejará que use tanto como haya disponible en ese momento en el clúster. Quedaría por tanto la sección resources de la siguiente forma:
```yaml
resources:
    requests:
      memory: 160Mi
      cpu: 5m
    limits:
      memory: 450Mi
```

Es decir, cuando el controller de kubernetes mande crear un nuevo pod, se buscará un nodo con al menos `160Mb` libres de memoria y `0.005 cores` de CPU. Si en algún momento la aplicación sobrepasa los `450Mb` de memoria, la aplicación será terminada con un OOM.


## Cómo optimizar el uso de memoria de la JVM en contenedores docker
Hasta aquí se han establecido los resources para una aplicación existente, ya podría funcionar de una manera bastante optimizada en un clúster de kubernetes, pero se puede optimizar, dependiendo de la configuración del heap size en la jvm, bastante más.

**Java**, de la versión 10 en adelante (esta característica fue también incluida más tarde en la versión 8 a partir de la build `8u191`), **reconoce los límites de memoria y cpu de su contenedor docker** (cgroup resource awareness); a partir de estos valores, establece automáticamente un heap size, a no ser que se indique uno manualmente. Hasta aquí todo bien, el “problema” es que, por defecto, establece el parámetro `MaxRAMPercentage` en un 25%, lo que hará que el heap size máximo sea de aproximadamente una cuarta parte de la memoria disponible en el contenedor. Un desperdicio. Se puede comprobar desplegando de nuevo el pod con la configuración de `resources` incluida en el recurso deployment:
```
$ kubectl delete -f ./k8s/resources.yaml
$ kubectl apply -f ./k8s/resources.yaml
```
Y entrando en el contenedor de la aplicación en minikube y ejecutando el siguiente comando:
´´´
$	java -XX:+PrintFlagsFinal -version | grep MaxHeapSize
´´´

Obteniendo como resultado un `MaxHeapSize` de `132Mb` (para un límite máximo establecido de `450Mb`). Por tanto, si se modifica el valor del parámetro `MaxRAMPercentage` para la JVM, se puede **conseguir que la aplicación haga uso de un mayor porcentaje de la memoria disponible** en el contenedor para memoria heap, lo que se traduce en una menor demanda de memoria en el clúster de kubernetes para correr esa misma aplicación en las mismas condiciones. Un valor más óptimo que puede dar muy buenos resultados es `MaxRAMPercentage=75`.

Aún quedaría un 25% libre para SO y non-heap (class metadata, JIT complied code, thread stacks, GC…), más que suficiente. Para comprobarlo, se puede ejecutar directamente dentro del contenedor el comando:

```
java -XX:MaxRAMPercentage=75 -XX:+PrintFlagsFinal -version | grep MaxHeapSize
```

Ahora la aplicación dispone de casi `350Mb` para heap, por lo que ya se podría establecer un valor mucho menor del parámetro `resources.limits.memory` para el deployment. Un ahorro considerable.

Por tanto, y a modo **recomendación general**:
- Para el recurso CPU se debería hacer uso del parámetro `requests` pero no de `limits`
- Establecer el Heap de la JVM al 75% del parámetro `requests.limits.memory`

Algo a tener en cuenta es el uso de esta característica (cgroup resource awareness) en **versiones de java 8 anteriores a la build 191 o en java 9**, donde no está presente por defecto pero se puede activar manualmente con los parámetros `XX:+UnlockExperimentalVMOptions` y `XX:+UseCGroupMemoryLimitForHeap`

Más información en este enlace: [Oracle blog | java-se-support-for-docker-cpu-and-memory-limits](https://blogs.oracle.com/java-platform-group/java-se-support-for-docker-cpu-and-memory-limits)


## Troubleshooting de pods java en kubernetes
Por último, a modo de ayuda al **“troubleshooting”**, es bastante útil comprender los motivos por los que kubernetes puede terminar con un pod. Con el comando:
```
kubectl describe pod
```

Se podrá descubrir la causa por la que kubernetes ha “matado” un pod. Si el motivo es `OOMKilled` es bastante obvio, ya que como se ha descrito anteriormente, ha intentado usar más RAM que el memory limit.

El problema es cuando aparecen los **códigos de salida** `137` o `143`, muy comunes en microservicios Java. Una aplicación Java puede terminar retornando un código de salida concreto llamando a System.exit(n) donde n es el código de salida, o puede terminar recibiendo una señal externa como `SIGKILL` o `SIGTERM`, en cuyo caso el código de salida vendrá calculado por la expresión:
```
EXIT-CODE = 128 + SIGNAL-CODE
```

El código para `SIGKILL` es `9`, mientras que el código de `SIGTERM` es `15`. Así, se puede fácilmente deducir que cuando el código de salida es `137`, se ha recibido una `SIGKILL`, mientras que el código `143` se refiere a la llegada de un `SIGTERM`.

El motivo por el que se recibe uno u otro es el siguiente: una aplicación Java puede interceptar un `SIGTERM` con un shutdown hook y poder así realizar las operaciones de limpieza antes de terminar la aplicación, mientras que un `SIGKILL` no podrá ser interceptado.

Cuando kubernetes quiere acabar con la vida de un pod, SIEMPRE enviará primero un `SIGTERM`, para dar al pod la oportunidad de realizer un “shutdown gracefully”. Si el pod sigue en estado “running” tras unos segundos, enviará un `SIGKILL`.

El proceso desglosado es el siguiente:
-	Lanza los preStop hooks
-	Envía `SIGTERM` a los contenedores
-	Espera un periodo de gracia de `30` segundos por defecto (configurable) para dar tiempo a los contenedores a realizar las operaciones de limpieza
-	Si tras dicho periodo siguen funcionando, envía `SIGKILL` a los contnedores

Los principales motivos por los que kubernetes termina un pod de forma inesperada son:
-	El nodo se queda sin recursos
-	Falla el health check de liveness probe

Otros motivos, en este caso esperados, por los que kubernetes terminará con un pod son:
-	Rolling update a la hora de realizer un deployment
-	Drenado de un nodo

Todo el código se encuentra disponible en el repositorio https://github.com/MasterCloudApps-Projects/Java-Kubernetes/tree/master/resources

