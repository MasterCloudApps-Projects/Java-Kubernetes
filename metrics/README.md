# Monitorización y visualización de logs de una aplicación java en kubernetes
A continuación, se va a proceder a analizar qué nos ofrecen actualmente los diferentes frameworks de java orientados a microservicios y cloud para facilitar la exposición de métricas a un operador prometheus tanto de la JVM como customizadas.

## Requisitos
    - Conocimientos básicos de kubernetes
    - Maven
    - Cluster local de kubernetes, por ejemplo Minikube
    - Kubectl

## En qué consiste el análisis
Los frameworks java en los que se va a centrar el análisis son **Quarkus**, **Micronaut** y **Springboot**. Todos ellos se pueden ejecutar con **Maven**, por lo que se podrá compilar cualquiera de ellos con el comando `mvn package` para generar los correspondientes jar. Algo que tienen también en común es que todos ellos cuentan con una web muy similar donde se pueden generar los correspondientes boilerplates, seleccionando la paquetización y las dependencias con las que el proyecto se quiere inicializar:
-	Quarkus: https://code.quarkus.io/
-	Micronaut: https://micronaut.io/launch/
-	Springboot: https://start.spring.io/

Una vez se descargan los proyectos de las correspondientes webs, y teniendo maven instalado, ya serían proyectos funcionales con las dependencias seleccionadas que se podrían ejecutar. Además, todos ellos cuentan con modo desarrollo permitiendo “hot swap” (y funciona muy bien en los tres) para tener un entorno de desarrollo ágil y rápido:
-	Quarkus: `./mvnw compile quarkus:dev`
-	Micronaut: `./mvnw mn:run`
-	Springboot: el modo desarrollo requerirá incluirá la dependencia DevTools y además arrancar la aplicación desde un IDE con plugin SpringBoot (Eclipse, IntelliJ, VS Code..).

Una vez creado **Prometheus, Grafana y Loki** estén funcionando en el clúster de kubernetes (se utilizarán **Helm charts** para la instalación), el análisis será idéntico para cada uno de los frameworks:
1. Generar el proyecto boilerplate desde la web, añadiendo las dependencias necesarias para exponer las métricas de la JVM utilizando **micrometer** o similar.
2. Crear una clase `ScrapingMetrics`, simulando el comportamiento que tendría el scraping de una web externa, con el objetivo de exponer dos métricas customizadas, una de tipo `Counter` (cuántas veces se ha realizado el scraping desde el inicio de la app) y otra de tipo `Gauge` (tiempo que ha tardado en realizarse el último scraping).
3. Añadir `yamls` básicos tanto de `deployment` como de `service` para el despliegue en el clúster de kubernetes local (usaré minikube para probar los ejemplos). Crear a su vez un `yaml` adicional de tipo `ServiceMonitor`, donde se le indicará al operador de prometheus dónde y cómo leer las métricas de la aplicación. Desplegar el servicio.
4. Configurar un dashboard en **Grafana** mediante el el datasource de **Prometheus** mostrando las métricas tanto de la JVM como customizadas. Establecer una alerta para una de las métricas.
5. Por último, añadir un segundo panel al dashboard de **Grafana** mediante el datasource de **Loki** mostrando los logs de la aplicación junto a las métricas.

## Motivación a la hora de elegir el stack de monitorización y visualización de logs
Hoy en día el monitoreo una aplicación es algo esencial para cualquier equipo. Además, la visualización de logs es crucial en el análisis de errores e incidencias, y sobre todo cuando existen diversos entornos, la centralización de todos estos datos es indispensable.

Con un uso muy extendido, open source e inicialmente desarrollado por SoundCloud en 2012 y más tarde adoptado por la CNCF (Cloud Native Computing Foundation), **Prometheus** se postula como una de las principales opciones a la hora de llevar a cabo estas tareas de lectura de métricas para monitoreo y sistema de alertas. Además, se complementa muy bien con **Grafana**, ya que se puede establecer Prometheus como datasource y crear dashboards gráficos muy fácilmente.

Para lectura de logs, también open source y desarrollado por Grafana, **Loki** se autodefine como *“like prometheus, but for logs”*. No hace falta mencionar que se integra perfectamente con Grafana.


## Preparar stack de monitorización y visualización de logs
Lo primero será echar a andar tanto **Prometheus** como **Grafana** en el clúster de Kubernetes, para lo que se utilizarán **Helm Charts** (package manager para kubernetes). En minikube es muy sencillo activarlo:
```
$ minikube addons list
$ minikube addons enable helm-tiller
```

A la hora de elegir un chart de Helm de prometheus, hay varias opciones. Una muy cómoda y completa es el chart [prometheus-operator](https://hub.helm.sh/charts/stable/prometheus-operator).
```
$ helm repo update
$ helm install –-name metrics stable/prometheus-operator --version 9.3.1
```

Este chart simplifica el despliegue y configuración de todo el stack completo (prometheus, alertmanager, grafana y el resto de componentes relacionados). Se puede observar en el clúster la aparición de diversos pods para el stack mencionado.

![Screenshot Prometheus Operator in Minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/prometheus-operator-ready.png "Screenshot Prometheus Operator in Minikube")

A partir de aquí, se podrá hacer uso del comando `kubectl port-forward` para acceder a cualquiera de los servicios (Prometheus en el puerto `9090` y Grafana en el `3000` principalmente). Por defecto, prometheus operator crea varios `targets` desde los que Prometheus irá leyendo métricas; esto es gestionado mediante `ServiceMonitors` y se tendrán que crear para cada aplicación que se despliegue con el fin de que Prometheus sea capaz de leer sus métricas, donde se indicará el endpoint del que leerlas, el servicio en el que están, el puerto y la frecuencia con que debe actualizarlas.

En segundo lugar habrá que instalar el stack de **Loki** *“like Prometheus, but for logs”* para la visualización de logs en el dashboard de **Grafana**. Una herramienta open-source, muy liviana y desarrollada por Grafana para exactamente estos casos de uso en k8s (sobra decir que la integración es excelente).

Un stack de Loki se basa en tres componentes:
-	**Promtail**, el agente responsible de recoger los logs y enviarlos a Loki.
-	**Loki**, el servdor principal, encargado de almacenar los logs y procesar las queries.
-	**Grafana** para visualización de los logs.

De forma análoga a Prometheus, se va a instalar Loki mediante [Helm charts](https://grafana.github.io/loki/charts/). Existe un `loki-stack` que deja todo configurado como en el caso anterior, pero ya incluiría Grafana, y como ya está instalado en el clúster de k8s de minikube, se van a instalar simplemente Promtail y Loki por separado.
```
$ helm repo add loki https://grafana.github.io/loki/charts
$ helm repo update
$	helm upgrade --install loki loki/loki
$	helm upgrade --install promtail loki/promtail --set "loki.serviceName=loki"
```

Una vez completados ambos despliegues, ya estarán dos nuevos pods en minikube:

![Screenshot Loki Stack in Minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/loki-stack-ready.png "Screenshot Prometheus Loki Stack in Minikube")

Con esto ya estaría todo el stack listo.

## Spring Boot
### Generar el proyecto 
En primer lugar, **Spring Boot** (en su versión 2.3.3) ofrece una solución completa para exposición de métricas mediante el módulo Actuator.

Una vez en el generador de código en su web **https://start.spring.io/**, se configura un proyecto **maven**, con **java 11**, y las dependencias **Actuator** (implementa **Micrometer**), **DevTools** (para tener hotswap en modo dev), y **Spring WEB**:

![Screenshot SpringBoot Initializr](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/springboot-initializr.png "Screenshot SpringBoot Initializr")

De forma adicional al boilerplate seleccionado, hará falta añadir la dependencia `micrometer-registry-prometheus` para exponer las métricas *"en formato Prometheus"*: 
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Implementar métricas customizadas
Una vez descargado el proyecto, simplemente se va a añadir una clase `ScrapingMetrics.java`. Será necesario implementar la interfaz [MeterBinder](https://www.javadoc.io/doc/io.micrometer/micrometer-core/1.1.0/io/micrometer/core/instrument/binder/MeterBinder.html) de **Micrometer**, para añadir las métricas creadas en el método `bindTo()` al registro de métricas que expone el servicio. Esta clase creará un registro para métricas, y simulará el *"scraping"* de una web, con un temporizador que cada 10 segundos realizará el scraping de nuevo. Por tanto, se añadirán dos métricas customizadas:

  -	"times_website_got_scraped_counter" de tipo `Counter`
  -	"time_spent_scraping_last_attempt" de tipo `Gauge`

Ya que es una simulación, el tiempo que le llevará a la aplicación hacer el scraping será simplemente un número aleatorio entre 1 y 10 segundos. Además, se añade algo de logging para más tarde visualizarlo.


```java
@Component
public class ScrapingMetrics implements MeterBinder {

	Random random = new Random();
	private MeterRegistry meterRegistry = null;
	private Counter timesWebGotScrapedCounter = null;
	private Gauge timeSpentScrapingLastAttemptGauge = null;

	@Override
	public void bindTo(final MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		timesWebGotScrapedCounter = meterRegistry.counter("times_website_got_scraped_counter");
	}

	@Scheduled(fixedRate = 10000)
	@Timed(description = "Timer to scrap website")
	public void scrapWebsite() throws InterruptedException {
		System.out.println("Start scraping website...");
		int ms = random.nextInt(9) + 1;
		setTimeScrapingLastAttemptMetric(ms);
		Thread.sleep(1000L * ms);
		timesWebGotScrapedCounter.increment();
		System.out.println("Scraping finished");
	}

	private void setTimeScrapingLastAttemptMetric(int ms) {
		if (timeSpentScrapingLastAttemptGauge != null) {
			meterRegistry.remove(timeSpentScrapingLastAttemptGauge);
		}

		timeSpentScrapingLastAttemptGauge = Gauge.builder("time_spent_scraping_last_attempt", this, value -> ms)
				.register(meterRegistry);
	}
}
```

Adicionalmente, un dockerfile `./Dockerfile` muy sencillo, ya que spring-boot no viene con uno por defecto:
```dockerfile
FROM openjdk:11-jre-slim
USER root
WORKDIR /
COPY target/metrics-1.0.0.jar /opt/

# Random user for k8s
USER 1000300
EXPOSE 8080

ENTRYPOINT ["java","-jar","/opt/metrics-1.0.0.jar"]
```

### Crear recursos para kubernetes
Y un `yaml` básico con los recursos **deployment** y **service**, con el fin de poder ser desplegado en k8s `spring-boot/k8s/scraping.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: scraping-springboot
  name: scraping-springboot
spec:
  replicas: 1
  selector:
    matchLabels:
      app: scraping-springboot
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: scraping-springboot
    spec:
      securityContext:
         runAsUser: 1000300
      containers:
      - image: scraping:spring-boot
        name: scraping-springboot
        ports:
        - name: web
          containerPort: 8080
        imagePullPolicy: Never
        securityContext:
          allowPrivilegeEscalation: false
---
apiVersion: v1
kind: Service
metadata:
  name: scraping-springboot
  labels:
    app: scraping-springboot
    k8s-app: scraping-springboot
spec:
  selector:
    app: scraping-springboot
  ports:
    - name: web
      port: 8080
      protocol: TCP
      targetPort: web
```

Aunque se despliegue la aplicación en kubernetes, tal y como se ha mencionado antes, habría que crear un recurso de tipo `ServiceMonitor` donde se habilitará la lectura de métricas por parte de Prometheus:
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: scraping-springboot
  labels:
    release: metrics
spec:
  selector:
    matchLabels:
      app: scraping-springboot
  endpoints:
  - port: web
    path: '/actuator/prometheus'
    interval: 10s
    honorLabels: true
```

Ya solo faltará desplegar el servicio en k8s, construyendo previamente el proyecto y la imagen docker:
```
$ cd spring-boot
$ mvn package
$ eval $(minikube docker-env)
$ docker build –t scraping:spring-boot .
$ kubectl apply –f ./k8s/
```

![Screenshot SpringBoot deployed in minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/springboot-ready.png "Screenshot SpringBoot deployed in minikube")

### Configurar un dashboard en Grafana mostrando métricas de la JVM y customizadas
Una vez creado el `ServiceMonitor`, la aplicación de springboot debería aparecer en los “targets” de Prometheus:
```
$ kubectl port-forward prometheus-metrics-prometheus-operato-prometheus-0 9090
$ http://localhost:9090/targets
```

![Screenshot SpringBoot target in Prometheus](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/springboot-target.png "Screenshot SpringBoot target in Prometheus")

Una vez Prometheus es capaz de leer las métricas de la aplicación, está todo listo para crear un dashboard con métricas de la aplicación spring-boot. De igual forma se hace un port-forward para acceder a Grafana:
```
$ kubectl port-forward metrics-grafana-5c4d567bf4-dp2hj 3000
$ http://localhost:3000
```
Los datos de acceso por defecto a Grafana son usuario `admin` y contraseña `prom-operator`.

Ya en Grafana, se van a crear dos dashboards, ambos partiendo del datasource de Prometheus que ya está creado gracias a que `prometheus-operator` lo deja todo configurado. Uno para monitorizar el estado de la JVM en la aplicación de scraping, y otro para monitorización de las métricas customizadas que se han creado y visualización de los logs. Para visualizar el estado de la JVM, existe un [dashboard de Grafana para métricas de micrometer](https://grafana.com/grafana/dashboards/4701), por lo que simplemente se debe importar utilizando la id del dashboard:
 
![Screenshot import JVM dashboard in Grafana](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/grafana-import-jvm-dashboard.png "Screenshot import JVM dashboard in Grafana")

El dashboard aparecerá con muchas **métricas relevantes de la JVM** listas para ser monitoreadas, con un selector de aplicaciones de las que se puede leer, por ahora solo spring-boot:

![Screenshot JVM dashboard in Grafana](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/jvm-dashboard-ready.png "Screenshot JVM dashboard in Grafana")

Para el dashboard de las **métricas customizadas**, se creará uno desde cero, utilizando el datasource de prometheus y creando dos paneles, uno de tipo `graph` para visualizar el tiempo que springboot ha tardado en hacer el scraping de la web, y uno de tipo `stat` para el contador de veces que se ha realizado el scraping.


![Screenshot Stat panel for SpringBoot](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-panel-stat-spring-boot.png "Screenshot Stat panel for SpringBoot")

De forma complementaria se puede establecer una alarma, por ejemplo cuando el scraping de la web tarde más de 8 segundos:

![Screenshot alarm in Stat panel for SpringBoot](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-panel-alert-spring-boot.png "Screenshot alarm in Stat panel for SpringBoot")

El dashboard de las métricas customizadas nos informaría pues, de un vistazo, del estado del proceso de scraping interno que la aplicación springboot está llevando a cabo:

![Screenshot custom metrics dashboard SpringBoot](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-dashboard-spring-boot.png "Screenshot custom metrics dashboard SpringBoot")

Ahora solo faltaría la visualización de los logs en este mismo dashboard. Por tanto, el siguiente paso será crear en Grafana un **datasource para Loki** (para Prometheus no hizo falta ya que venía creado) usando la url del service `http://loki:3100/`:

![Screenshot Loki datasource in Grafana](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/grafana-loki-datasource.png "Screenshot Loki datasource in Grafana")

Y para visualizar los logs en el mismo dashboard de las métricas customizadas, simplemente habría que añadir un panel de tipo `logs` al dashboard de custom metrics, usando el datasource loki y seleccionando el pod concreto:

![Screenshot logs panel in Grafana for SpringBoot](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-panel-logs-spring-boot.png "Screenshot logs panel in Grafana for SpringBoot")

Quedando un dashboard muy completo con toda la información que se necesitaría para monitorear adecuadamente el servicio:

![Screenshot full Grafana dashboard for SpringBoot](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-dashboard-spring-boot-logs.png "Screenshot full Grafana dashboard for SpringBoot")


## Quarkus
### Generar el proyecto 
En segundo lugar se analiza Quarkus (actualmente en su versión 1.8.1). Tras ver en su documentación que implementa la especificación de **MicroProfile Metrics** a través de la extensión `SmallRye Metrics`, se va a generar un boilerplate desde `mvn` añadiendo simplemente la extensión mencionada:

```
$ mvn io.quarkus:quarkus-maven-plugin:1.8.1.Final:create -DprojectGroupId=com.javieraviles -DprojectArtifactId=metrics -Dextensions="smallrye-metrics"
```

Si ya existiese el proyecto y simplemente se quiere añadir la extensión, se utilizaría:

```
$ ./mvnw quarkus:add-extension -Dextensions="smallrye-metrics"
```

De forma adicional, se necesitará la extensión `scheduler` para el timer de la clase `ScrapingMetrics.java`:
```
./mvnw quarkus:add-extension -Dextensions="scheduler"
```

### Implementar métricas customizadas
Una vez descargado el proyecto, simplemente se va a añadir una clase `ScrapingMetrics.java`. Esta clase, al igual que en el caso de spring-boot, contará con un temporizador que cada 10 segundos realizará el scraping de nuevo. Por tanto, se añadirán dos métricas customizadas:

  -	"timesWebsiteGotScrapedCounter" de tipo `Counter`
  -	"timeSpentScrapingLastAttempt" de tipo `Gauge`

```java
@ApplicationScoped
public class ScrapingMetrics {

    Random random = new Random();
    private int timeSpentScrapingLastAttempt = 0;

    @Counted(name = "timesWebsiteGotScrapedCounter", description = "How many times the website has been scraped.")
    @Scheduled(every="10s")
    public void scrapWebsite() throws InterruptedException {
        System.out.println("Start scraping website...");
		timeSpentScrapingLastAttempt =random.nextInt(9) + 1;
		Thread.sleep(1000L * timeSpentScrapingLastAttempt);
		System.out.println("Scraping finished");
    }

    @Gauge(name = "timeSpentScrapingLastAttempt", unit = MetricUnits.SECONDS, description = "time spent scraping (last attempt)")
    public int timeSpentScrapingLastAttempt() {
        return timeSpentScrapingLastAttempt;
    }

}
```

Un detalle adicional importante, es que Quarkus no implementa **Micrometer** como tal, pero se pueden activar en modo *"compatibilidad con Micrometer"* mediante la proerty `quarkus.smallrye-metrics.micrometer.compatibility` (por defecto con valor `false`) en el archivo `quarkus/src/main/resources/application.properties`:
```
quarkus.smallrye-metrics.micrometer.compatibility=true
``` 

Por otro lado, Quarkus ya proporciona los dockerfiles `quarkus/src/main/docker`, por lo que ese punto estaría cubierto.

### Crear recursos para kubernetes
Se creará un `yaml` básico con los recursos **deployment** y **service**, prácticamente idéntico al caso anterior, con el fin de poder ser desplegado en k8s `quarkus/k8s/scraping.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: scraping-quarkus
  name: scraping-quarkus
spec:
  replicas: 1
  selector:
    matchLabels:
      app: scraping-quarkus
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: scraping-quarkus
    spec:
      securityContext:
         runAsUser: 1000300
      containers:
      - image: scraping:quarkus
        name: scraping-quarkus
        ports:
        - name: web
          containerPort: 8080
        imagePullPolicy: Never
        securityContext:
          allowPrivilegeEscalation: false
---
apiVersion: v1
kind: Service
metadata:
  name: scraping-quarkus
  labels:
    app: scraping-quarkus
    k8s-app: scraping-quarkus
spec:
  selector:
    app: scraping-quarkus
  ports:
    - name: web
      port: 8080
      protocol: TCP
      targetPort: web
```

Y de nuevo el `ServiceMonitor`, esta vez con la ruta a las métricas de Quarkus:
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: scraping-quarkus
  labels:
    release: metrics
spec:
  selector:
    matchLabels:
      app: scraping-quarkus
  endpoints:
  - port: web
    path: '/metrics'
    interval: 10s
    honorLabels: true
```

Ya solo faltará desplegar el servicio en k8s, construyendo previamente el proyecto y la imagen docker:
```
$ cd quarkus
$ ./mvnw package 
$ eval $(minikube docker-env)
$ docker build -t scraping:quarkus -f ./src/main/docker/Dockerfile.jvm .
$ kubectl apply –f ./k8s/
```

![Screenshot Quarkus deployed in minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/quarkus-ready.png "Screenshot Quarkus deployed in minikube")

### Configurar un dashboard en Grafana mostrando métricas de la JVM y customizadas
Una vez creado el `ServiceMonitor`, la aplicación de Quarkus debería aparecer en los “targets” de Prometheus:

![Screenshot Quarkus target in Prometheus](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/quarkus-target.png "Screenshot Quarkus target in Prometheus")

El dashboard de la JVM no hace falta tocarlo, ya que ahora el selector permitirá seleccionar no solo el servicio de spring-boot si no también el de Quarkus:
![Screenshot JVM dashboard with selector](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/jvm-dashboard-selector.png "Screenshot JVM dashboard with selector")


En cuanto al dashboard creado anteriormente para las métricas customizadas, se modificará, incluyendo también las métricas del servicio Quarkus para una visualización combinada:

![Screenshot Stat panel for Quarkus](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-panel-stat-quarkus.png "Screenshot Stat panel for Quarkus")

![Screenshot Graph panel for Quarkus](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-panel-graph-quarkus.png "Screenshot Graph panel for Quarkus")

Además se combinarán los logs de ambas en el panel de Loki. Ahora, el dashboard de las métricas customizadas nos informaría pues, de un vistazo, del estado del proceso de scraping interno que ambas aplicaciones están llevando a cabo:

![Screenshot custom metrics dashboard Quarkus](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-dashboard-quarkus-logs.png "Screenshot custom metrics dashboard Quarkus")


## Micronaut
### Generar el proyecto 
En tercer lugar se va a analizar qué ofrece **Micronaut** (actualmente en su versión 2.0.3) para facilitar la exposición de métricas. Resaltar que soportan una barbaridad de "reporters" diferentes. De manera similar al caso de springboot, implementan **Micrometer**, por lo que se descargará directamente en su web https://micronaut.io/launch/ el boilerplate donde se seleccionarán los **features** que se quieren añadir al proyecto de base. En este caso será un proyecto Java 11, maven, con el feature [micrometer-prometheus](https://micronaut-projects.github.io/micronaut-micrometer).

Con esto, en teoría, el servicio ya expondría métricas de la JVM en formato Prometheus en el endpoint `/prometheus`. Pero hace falta activarlo en el archivo `micronaut/resources/application.yaml`:
```yaml
endpoints:
  prometheus:
    sensitive: false
micronaut:
  application:
    name: metrics
  metrics:
    enabled: true
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: PT1M
```

### Implementar métricas customizadas
Ahora se creará la clase `ScrapingMetrics.java`. Esta clase, al igual que en los casos anteriores, contará con un temporizador que cada 10 segundos realizará el scraping de nuevo. Por tanto, se añadirán dos métricas customizadas:

  -	"mn_times_website_got_scraped_counter" de tipo `Counter`
  -	"mn_time_spent_scraping_last_attempt" de tipo `Gauge`

```java
@Singleton
public class ScrapingMetrics {

    private MeterRegistry meterRegistry;
    Random random = new Random();
    private Counter timesWebGotScrapedCounter = null;
	private Gauge timeSpentScrapingLastAttemptGauge = null;

    public ScrapingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        timesWebGotScrapedCounter = meterRegistry.counter("mn_times_website_got_scraped_counter");
    }

    @Scheduled(fixedDelay = "10s") 
    void scrapWebsite() throws InterruptedException {
        System.out.println("Start scraping website...");
		int ms = random.nextInt(9) + 1;
		setTimeScrapingLastAttemptMetric(ms);
		Thread.sleep(1000L * ms);
		timesWebGotScrapedCounter.increment();
		System.out.println("Scraping finished");
    }


    private void setTimeScrapingLastAttemptMetric(int ms) {
		if (timeSpentScrapingLastAttemptGauge != null) {
			meterRegistry.remove(timeSpentScrapingLastAttemptGauge);
		}

		timeSpentScrapingLastAttemptGauge = Gauge.builder("mn_time_spent_scraping_last_attempt", this, value -> ms)
				.register(meterRegistry);
	}

}
```

Por otro lado, Micronaut ya proporciona un archivo Dockerfile `quarkus/Dockerfile`, por lo que ese punto estaría cubierto como en el caso de Quarkus.

### Crear recursos para kubernetes
Se creará un `yaml` básico con los recursos **deployment** y **service**, prácticamente idéntico a los casos anteriores, con el fin de poder ser desplegado en k8s `micronaut/k8s/scraping.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: scraping-micronaut
  name: scraping-micronaut
spec:
  replicas: 1
  selector:
    matchLabels:
      app: scraping-micronaut
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: scraping-micronaut
    spec:
      securityContext:
         runAsUser: 1000300
      containers:
      - image: scraping:micronaut
        name: scraping-micronaut
        ports:
        - name: web
          containerPort: 8080
        imagePullPolicy: Never
        securityContext:
          allowPrivilegeEscalation: false
---
apiVersion: v1
kind: Service
metadata:
  name: scraping-micronaut
  labels:
    app: scraping-micronaut
    k8s-app: scraping-micronaut
spec:
  selector:
    app: scraping-micronaut
  ports:
    - name: web
      port: 8080
      protocol: TCP
      targetPort: web
```

Y de nuevo el `ServiceMonitor`, esta vez con la ruta a las métricas de Micronaut:
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: scraping-micronaut
  labels:
    release: metrics
spec:
  selector:
    matchLabels:
      app: scraping-micronaut
  endpoints:
  - port: web
    path: '/prometheus'
    interval: 10s
    honorLabels: true
```

Ya solo faltará desplegar el servicio en k8s, construyendo previamente el proyecto y la imagen docker:
```
$ cd quarkus
$ ./mvnw package 
$ eval $(minikube docker-env)
$ docker build -t scraping:micronaut .
$ kubectl apply –f ./k8s/
```

![Screenshot Micronaut deployed in minikube](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/micronaut-ready.png "Screenshot Micronaut deployed in minikube")

### Configurar un dashboard en Grafana mostrando métricas de la JVM y customizadas


De nuevo el dashboard de la JVM no hace falta tocarlo, ya que ahora el selector permitirá seleccionar entre cualquiera de las tres aplicaciones. En cuanto al dashboard creado anteriormente para las métricas customizadas, se modificará, incluyendo también las métricas del servicio Micronaut para una visualización combinada:

![Screenshot custom metrics dashboard Micronaut](https://raw.githubusercontent.com/MasterCloudApps-Projects/Java-Kubernetes/master/metrics/assets/metrics-dashboard-micronaut-logs.png "Screenshot custom metrics dashboard Micronaut")



Todo el código se encuentra disponible en el repositorio https://github.com/MasterCloudApps-Projects/Java-Kubernetes/tree/master/metrics



