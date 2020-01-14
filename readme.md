### Prerequisites

- install kubectl
- install k9s (https://github.com/derailed/k9s)
- install skaffold (https://skaffold.dev/)

### Optional:
- install kubectx (kctx) and kubens (kns) (https://github.com/ahmetb/kubectx)

### Getting started
- install k3s
```shell script
curl -sfL https://get.k3s.io | sh -
```

- import kube config yaml
```shell script
sudo cat /etc/rancher/k3s/k3s.yaml | sed 's/name: default/name: k3s/g' | sed 's/cluster: default/cluster: k3s/g' | sed 's/user: default/user: k3s/g' | sed 's/current-context: default/current-context: k3s/g' > ~/.kube/k3s.yml
echo "export KUBECONFIG=~/.kube/config:~/.kube/k3s.yml" >> ~/.bashrc
source ~/.bashrc
```

### generate project
- start.spring.io - web, actuator, micrometer prometheus

### make some example controller
```java
@RestController
public class LivenessController {

    @GetMapping("/liveness")
    public ResponseEntity<Map<String, String>> get() {
        return ResponseEntity.ok(Collections.singletonMap("status", "UP"));
    }
}
```

### configure jib

```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>1.8.0</version>
    <configuration>
        <from>
            <image>gcr.io/distroless/java:11</image>
        </from>
        <to>
            <image>nexus.esolutions.ro/demo-k3s</image>
        </to>
        <container>
            <jvmFlags>
                <jvmFlag>-Djava.security.egd=file:/dev/./urandom</jvmFlag>
            </jvmFlags>
        </container>
    </configuration>
</plugin>
```

### test jib build is working 

```shell script
mvn jib:build
mvn jib:dockerBuild
```

### dockerfile (optional) - for jib building with docker

```dockerfile
FROM gcr.io/distroless/java:11
ADD target/demo-k3s.jar demo-k3s.jar
ENV JAVA_OPTS=""
ENTRYPOINT exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar demo-k3s.jar
```

### k8s deployment
* create image pull secrets
  * from file:
    ```shell script
     kubectl create secret generic nexus-registry-credentials --from-file=.dockerconfigjson=/home/marius/.docker/config.json --type=kubernetes.io/dockerconfigjson
    ```

  * from credentials:
    ```shell script
    kubectl create secret docker-registry nexus-registry-credentials --docker-server=nexus.esolutions.ro --docker-username=<username-here> --docker-password=<password-here> --docker-email=<email-here>
    ```

* create deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-k3s
  namespace: default
  labels:
    app: demo-k3s
spec:
  replicas: 1
  selector:
    matchLabels:
      app: demo-k3s
  template:
    metadata:
      labels:
        app: demo-k3s
    spec:
      imagePullSecrets:
      - name: nexus-registry-credentials
      containers:
      - name: demo-k3s
        image: nexus.esolutions.ro/demo-k3s
        ports:
        - containerPort: 8080
        resources:
          requests:
            cpu: 100m
            memory: 600M
          limits:
            cpu: 1
            memory: 1.2G
        livenessProbe:
          httpGet:
            path: /liveness
            port: 8080
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
        env:
        - name: JAVA_OPTS
          value: "-Xms200m -Xmx680m -XX:MaxMetaspaceSize=180m -XX:+UseG1GC -XX:+UseStringDeduplication -Duser.timezone=UTC -Dfile.encoding=UTF-8"
        - name: APP_ENV
          value: 'local'

```

### initialize skaffold
```bash
skaffold init --XXenableJibInit
```

### adjust some skaffold stuff - eg. tag policy or deployment files :)
```yaml
apiVersion: skaffold/v2alpha1
kind: Config
metadata:
  name: demo-k3s
build:
  tagPolicy:
    envTemplate:
      template: "{{.IMAGE_NAME}}:{{.USER}}"
  artifacts:
  - image: nexus.esolutions.ro/demo-k3s
    jib:
      args:
      - -Dmaven.test.skip
deploy:
  kubectl:
    manifests:
    - deployment/local/*.yml
```

### first build & deploy to k8s
- test build
     ```bash
    skaffold build 
    ```

- test deploy
     ```bash
    skaffold run --tail
    ```

- dev mode
    ```bash
    skaffold dev --no-cleanup
    ```

- debug mode
    ```bash
    skaffold debug
    ```

###expose deployment

- create a service
```bash
apiVersion: v1
kind: Service
metadata:
  name: demo-k3s
  namespace: default
spec:
  ports:
    - name: http
      port: 8080
      targetPort: 8080
    - name: debug
      port: 5005
      targetPort: 5005
  selector:
    app: demo-k3s
```

- create an ingress
```bash
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: demo-k3s
  namespace: default
  annotations:
    kubernetes.io/ingress.class: traefik
    #traefik.ingress.kubernetes.io/redirect-entry-point: https
    #traefik.ingress.kubernetes.io/redirect-permanent: "true"
spec:
  rules:
    - host: demo-k3s.localhost
      http:
        paths:
          - path: /
            backend:
              serviceName: demo-k3s
              servicePort: http
```

- play with skaffold
```shell script
skaffold dev --cleanup=false #no cleanup after dev cli closes
skaffold dev --skip-tests #to skip tests in dev mode 
skaffold dev --trigger=polling -- watch-poll-interval=1000 #to poll for changes once in a while
```