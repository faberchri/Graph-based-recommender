Graph-based Recommender
=======================

Graph-based recommender for BBC positive only feedback dataset using Gremlin graph query language.
See the excellent [blog post of M. Rodriguez](http://markorodriguez.com/2011/09/22/a-graph-based-movie-recommender-engine/) to get an idea of the basic principles of a graph-based recommender system.

How-To-Run
----------
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

Graph Structure
-----
Graphs queried for recommendations have the structure depicted below.
* Vertices in red: User
* Vertices in cyan: Shows
* Vertices in purple: Show Attributes (e.g. 'genres/comedy' or 'service/bbcfour')
* User -> Show edge: 'watched'
* Show -> Attribute edge: 'show has attribute'

If a user watches the same show more than once the graph contains multiple 'watched' edges between the user and the show.

![Alt text](sample-graph.svg)


Recommendation Strategies
-----
The recommendations are determined by the implementation of `IRecommendationStrategy`:
````groovy
interface IRecommendationStrategy{
	List<Vertex> recommendShowsToUser(Vertex user);
}
````

* `RankedCollaborativeFilteringRecommendationStrategy`: Pure collaborative filtering. Recommended are shows that have not been watched by the current user but by users that watched at least one of the shows that the current user watched. The retrieved shows are ranked by the number of shortest paths (length == 3) from the current user to the new shows.

    ```groovy
    class RankedCollaborativeFilteringRecommendationStrategy implements IRecommendationStrategy{
        List recommendShowsToUser(Vertex user){
            // find all path from current user to new shows in one other user distance, count number of paths to new show, order descending by counts 
            def showsWatchedByCurrentUser = [];
            // gremlin code follows
            return user.out('watched').aggregate(showsWatchedByCurrentUser).in('watched').out('watched').except(showsWatchedByCurrentUser).groupCount.cap.orderMap(T.decr).toList();
        }
    }
    ```
    If we apply this strategy on the sample graph with:

    ```bash
    titan-all-0.4.4/bin/gremlin.sh -e graphRecommender/main.groovy -t -u U1,U6,U12 . .
    ```
    We get:

    ```bash
    Your input arguments: [-t, -u, U1,U6,U12, ., .]
    -- Test graph loaded: tinkergraph[vertices:24 edges:36] --
    -- Some basic graph properties:
    ---- Number of vertices: 24
    ---- Number of vertices of type 'Show': 7
    ---- Number of vertices of type 'User': 14
    ---- Number of vertices of type 'Attribute': 3
    ---- Number of edges: 36
    ---- Number of edges of type 'hasAttribute': 11
    ---- Number of edges of type 'watched': 25
    ---------------------------------
    Calculating recommendations for 3 users
    -- Start recommendation calculation --
    -- Start recommendation calculation for user U1 (1/3) --
    ### Watched shows (5): ###
    S6 - <NA> - <NA>
    S7 - <NA> - <NA>
    S6 - <NA> - <NA>
    S1 - <NA> - <NA>
    S1 - <NA> - <NA>
    ### Recommendations (3, best first): ###
    1 - S5 - <NA> - <NA>
    2 - S2 - <NA> - <NA>
    3 - S3 - <NA> - <NA>
    -- Recommendation calculation for user U1 completed (1/3) --
    -- Start recommendation calculation for user U6 (2/3) --
    ### Watched shows (1): ###
    S5 - <NA> - <NA>
    ### Recommendations (1, best first): ###
    1 - S6 - <NA> - <NA>
    -- Recommendation calculation for user U6 completed (2/3) --
    -- Start recommendation calculation for user U12 (3/3) --
    ### Watched shows (3): ###
    S2 - <NA> - <NA>
    S3 - <NA> - <NA>
    S1 - <NA> - <NA>
    ### Recommendations (2, best first): ###
    1 - S6 - <NA> - <NA>
    2 - S7 - <NA> - <NA>
    -- Recommendation calculation for user U12 completed (3/3) --
    -- Recommendation calculation completed --
    Calculating recommendations for 3 users took 0.153 seconds (0.051 s per user).
    Application terminated without exceptions.
    ````

* `ItemAttributesConsideringRankedCollaborativeFilteringRecommendationStrategy`: Collaborative filtering with mixed in metadata / attributes similarity. Recommended are shows that have not been watched by the current user but by users that watched at least one of the shows that the current user watched OR shows that have a common attribute. The retrieved shows are ranked by the number of shortest paths (length == 3) from the current user to the new shows, either passing through an other user or a shared attribute.

    ```groovy
    class ItemAttributesConsideringRankedCollaborativeFilteringRecommendationStrategy implements IRecommendationStrategy{
        List recommendShowsToUser(Vertex user){
            // find all path from current user to new shows in one other user or an attribute distance, count number of paths to new show, order descending by counts 
            def showsWatchedByCurrentUser = [];
            return user.out('watched').aggregate(showsWatchedByCurrentUser).both('watched','hasAttribute').both('watched', 'hasAttribute').except(showsWatchedByCurrentUser).groupCount.cap.orderMap(T.decr).toList();
        }
    }
    ```
    If we apply this strategy on the sample graph with:

    ```bash
    titan-all-0.4.4/bin/gremlin.sh -e graphRecommender/main.groovy -t -u U1,U6,U12 . .
    ```
    We get:

    ```bash
    Your input arguments: [-t, -u, U1,U6,U12, ., .]
    -- Test graph loaded: tinkergraph[vertices:24 edges:36] --
    -- Some basic graph properties:
    ---- Number of vertices: 24
    ---- Number of vertices of type 'Show': 7
    ---- Number of vertices of type 'User': 14
    ---- Number of vertices of type 'Attribute': 3
    ---- Number of edges: 36
    ---- Number of edges of type 'hasAttribute': 11
    ---- Number of edges of type 'watched': 25
    ---------------------------------
    Calculating recommendations for 3 users
    -- Start recommendation calculation --
    -- Start recommendation calculation for user U1 (1/3) --
    ### Watched shows (5): ###
    S6 - <NA> - <NA>
    S7 - <NA> - <NA>
    S6 - <NA> - <NA>
    S1 - <NA> - <NA>
    S1 - <NA> - <NA>
    ### Recommendations (4, best first): ###
    1 - S5 - <NA> - <NA>
    2 - S2 - <NA> - <NA>
    3 - S3 - <NA> - <NA>
    4 - S4 - <NA> - <NA>
    -- Recommendation calculation for user U1 completed (1/3) --
    -- Start recommendation calculation for user U6 (2/3) --
    ### Watched shows (1): ###
    S5 - <NA> - <NA>
    ### Recommendations (5, best first): ###
    1 - S6 - <NA> - <NA>
    2 - S7 - <NA> - <NA>
    3 - S3 - <NA> - <NA>
    4 - S4 - <NA> - <NA>
    5 - S2 - <NA> - <NA>
    -- Recommendation calculation for user U6 completed (2/3) --
    -- Start recommendation calculation for user U12 (3/3) --
    ### Watched shows (3): ###
    S2 - <NA> - <NA>
    S3 - <NA> - <NA>
    S1 - <NA> - <NA>
    ### Recommendations (4, best first): ###
    1 - S7 - <NA> - <NA>
    2 - S6 - <NA> - <NA>
    3 - S4 - <NA> - <NA>
    4 - S5 - <NA> - <NA>
    -- Recommendation calculation for user U12 completed (3/3) --
    -- Recommendation calculation completed --
    Calculating recommendations for 3 users took 0.156 seconds (0.052 s per user).
    Application terminated without exceptions.
    ````

