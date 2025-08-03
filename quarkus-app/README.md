# Getting started with Quarkus

This is a minimal CRUD service exposing a couple of endpoints over REST.

Under the hood, this demo uses:

- RESTEasy to expose the REST endpoints
- REST-assured and JUnit 5 for endpoint testing

## Requirements

To compile and run this demo you will need:

- JDK 17+
- GraalVM

### Configuring GraalVM and JDK 17+

Make sure that both the `GRAALVM_HOME` and `JAVA_HOME` environment variables have
been set, and that a JDK 17+ `java` command is on the path.

See the [Building a Native Executable guide](https://quarkus.io/guides/building-native-image-guide)
for help setting up your environment.

## Building the application

Launch the Maven build on the checked out sources of this demo:

> mvn package

### Live coding with Quarkus

The Maven Quarkus plugin provides a development mode that supports
live coding. To try this out:

> mvn quarkus:dev

This command will leave Quarkus running in the foreground listening on port 8080.

1. Visit the default endpoint: [http://127.0.0.1:8080](http://127.0.0.1:8080).
    - Make a simple change to [src/main/resources/META-INF/resources/index.html](src/main/resources/META-INF/resources/index.html) file.
    - Refresh the browser to see the updated page.

### Run Quarkus in JVM mode

When you're done iterating in developer mode, you can run the application as a
conventional jar file.

First compile it:

> mvn package

Then run it:

> java -jar ./target/quarkus-app/quarkus-run.jar

Have a look at how fast it boots, or measure the total native memory consumption.

### Run Quarkus as a native executable

You can also create a native executable from this application without making any
source code changes. A native executable removes the dependency on the JVM:
everything needed to run the application on the target platform is included in
the executable, allowing the application to run with minimal resource overhead.

Compiling a native executable takes a bit longer, as GraalVM performs additional
steps to remove unnecessary codepaths. Use the  `native` profile to compile a
native executable:

> mvn package -Dnative

After getting a cup of coffee, you'll be able to run this executable directly:

> ./target/getting-started-1.0.0-SNAPSHOT-runner


## Google Login Demo

1. Set the environment variables `OIDC_ENABLED=true`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET` and the rest of the OAuth values listed in the project root README. Without these variables the application starts with OAuth disabled which is useful for local development and automated builds.
2. Start the application in dev mode:

```bash
mvn quarkus:dev
```

3. Visit [https://eventflow.opensourcesantiago.io/private](https://eventflow.opensourcesantiago.io/private). You will be redirected to authenticate with Google.
4. After login the private page shows your name, email and profile picture.

# Native build troubleshooting

If the native build fails with messages about `JGit-WorkQueue` threads or cached `Random` values, the JGit classes may have been
initialized during the image build. The application enables run time initialization for the problematic classes via the
`quarkus.native.additional-build-args` property, but new failures can be diagnosed by tracing object instantiations:

```bash
mvn package -Dnative \
  -Dquarkus.native.additional-build-args="--trace-object-instantiation=java.lang.Thread,java.util.Random,java.security.SecureRandom"
```

The output lists the classes responsible for creating threads or random number generators during the build so they can be added
to the run time initialization list.

