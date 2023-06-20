# Security-Lab: OpenID Connect in der Praxis

Demo repository for the workshop `Security-Lab: OpenID Connect in der Praxis` at the CloudLand2023.
Für den Workshop ist ein laufendes Kubernetes notwendig. Es beinhaltet zwei Services welche miteinander kommunizieren
und gemeinsam die Antwort `Hello <username> ` zurückgeben.

## Basis Setup

Neben dem laufenden Kubernetes Cluster ist folgendes notwendig:

* [git](https://git-scm.com/) + [GitHub Account](https://github.com)
* [kubectl](https://kubernetes.io/de/docs/tasks/tools/install-kubectl/)
* [flux](https://fluxcd.io/flux/installation/)

### Cluster Setup mit Colima

Setup mit Colima unter M1/M2 Macs

```shell
colima start -a aarch64 -c 8 -m 8 --kubernetes --network-address
```

Setup mit Colima unter Intel Macs

```shell
colima start -c 8 -m 8 --kubernetes --network-address
```

### Hostnamen eintragen

Folgendene Hostnamen sollten in der `/etc/hosts` Datei eingetragen werden damit der Zugriff leichter funktioniert:

* `keycloak.infrastructure`
* `middleware-server.services`
* `backend-service.services`

Für Colima kann die folgende Zeile verwendet werden:

```
# Security Lab: OIDC
192.168.106.2 	keycloak.infrastructure middleware-server.services backend-service.services
```

### Repo Fork

Anschließend dieses Repository Forken

### Infrastruktur auf dem Cluster installieren

1. GitHub Personal Access Token erstellen
2. Folgende Befehle ausführen

```shell
export GITHUB_TOKEN=<GH_ACCESS_TOKEN>
flux bootstrap github --owner <OWN_GH_USER_NAME> --personal --repository security-lab-oidc-integrations --path k8s/cluster/ --branch main
```
