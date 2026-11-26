# Starts DataSphere Migration Studio locally with the development-only 'dev' profile.
# Dev profile enables the local development administrator (see application-dev.yml). NEVER use it in production.
Set-Location $PSScriptRoot
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
