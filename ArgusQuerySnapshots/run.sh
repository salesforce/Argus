if [ ! -f ./target/argus-query-snapshots-1.0-SNAPSHOT-jar-with-dependencies.jar ]; then
    echo "JAR not found on target dir. Running mvn package..."
    mvn package
fi

java -jar target/argus-query-snapshots-1.0-SNAPSHOT-jar-with-dependencies.jar -DinputPath=queries.txt
