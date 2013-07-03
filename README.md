# EC2 Creator

Create EC2 instances for the purpose of imaging.

## Release

```
$ mvn package
```

Upload `target/ec2-instance-creator.hpi` to Jenkins.
You may have to restart Jenkins after uploading the plugin.

## Development

Generate an eclipse project with

```
mvn -DdownloadSources=true -DdownloadJavadocs=true -DoutputDirectory=target/eclipse-classes eclipse:eclipse
```
Run Jenkins with plugin installed automtically

```
mvn hpi:run
```
