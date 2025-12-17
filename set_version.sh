#!/bin/bash

VERSION=$1

if [ -z "$VERSION" ]; then
  echo "Usage: ./set_version.sh <version>"
  echo "Example: ./set_version.sh 3.7.0"
  exit 1
fi

echo "Updating project to version $VERSION"

# Upadate pom.xml
./mvnw versions:set -DnewVersion=$VERSION -DgenerateBackupPoms=false

# Update application.properties
# Use sed to replace quarkus.application.version=...
# Works on macOS/Linux
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' "s/quarkus.application.version=.*/quarkus.application.version=$VERSION/" quarkus-app/src/main/resources/application.properties
else
  sed -i "s/quarkus.application.version=.*/quarkus.application.version=$VERSION/" quarkus-app/src/main/resources/application.properties
fi

echo "Version updated to $VERSION in pom.xml and application.properties"
echo "You can now commit and tag:"
echo "  git commit -am 'chore: bump to $VERSION'"
echo "  git tag v$VERSION"
echo "  git push origin main v$VERSION"
