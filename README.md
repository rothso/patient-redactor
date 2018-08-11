# Patient Redactor
Tool for redacting patient names from specifically-formatted PDFs.


## Getting Started

Download the [latest build](https://github.com/rothso/patient-redactor/releases/latest) of the JAR and run the following command:

```sh
> java -jar patient-redactor-1.0.jar input.pdf
```

The name of the output file is the same as the name of the input file prefixed by `redacted-`.

#### Building from source
You can also edit and compile this project yourself:

```sh
# Linux
$ git clone https://github.com/rothso/patient-redactor.git
$ cd patient-redactor
$ ./gradlew
$ gradle build
$ gradle jar
```
```sh
# Windows
> git clone https://github.com/rothso/patient-redactor.git
> cd patient-redactor
> gradlew.bat
> gradle build
> gradle jar
```

---
*Built with :heart: for [MASS Free Clinic](http://www.massclinic.org/)*.
