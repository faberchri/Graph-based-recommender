Graph-based Recommender
=======================

Graph-based recommender for positive only feedback dataset using Gremlin graph query language.

####How-To-Run
1. Download Titan Graph Database v0.4.4 (all): http://thinkaurelius.github.io/titan/
2. Set the Java VM args for groovy/gremlin. The complete BBC 6.7 Mio ratings dataset needs at least 35Gb (!) of RAM. If you want to serialize the graph to disk you also need to increase the stack size. Hence:
    
    ```bash
    JAVA_OPTIONS="-Xms1g -Xmx35g -Xss100m"
    export JAVA_OPTIONS
    ```
3. Pass `main.groovy` to `gremlin.sh` in script mode (`-e`):
    
    ```bash
    titan-all-0.4.4/bin/gremlin.sh -e path/to/Graph-based-recommender/main.groovy -p dir/for/optional/graph/serialization -u some,user,ids,to,process path/to/BBC/dataset path/to/output/directory
    ```
