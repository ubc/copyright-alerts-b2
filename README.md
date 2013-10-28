copyright-alerts
================

Now using Gradle for dependency resolution. To download dependencies, run
`gradle build`. To generate Eclipse project files, run `gradle eclipse`. Gradle
build should copy the dependency jars into WebContent/WEB-INF/lib for
deployment. Blackboard API jars haven't been configured, so they'll have to be
added separately.
