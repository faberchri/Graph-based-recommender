Graph-based Recommender
=======================

Graph-based recommender for positive only feedback dataset using Gremlin graph query language.

####How-To-Run
1. Download Gremlin v2.5.0: https://github.com/tinkerpop/gremlin/wiki
2. Set the Java VM args for groovy/gremlin. The complete BBC 6.7 Mio ratings dataset needs at least 35Gb (!) of RAM. Hence:
    
    ```bash
    JAVA_OPTIONS="-Xms1g -Xmx35g"
    export JAVA_OPTIONS
    ```
3. Pass `main.groovy` to `gremlin.sh` in script mode (`-e`):
    
    ```bash
    gremlin-groovy-2.5.0/bin/gremlin.sh -e path/to/Graph-based-recommender/main.groovy -p dir/for/optional/graph/serialization -u some,user,ids,to,process path/to/BBC/dataset path/to/output/directory
    ```
