// inspired by: http://markorodriguez.com/2011/09/22/a-graph-based-movie-recommender-engine/

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

import java.text.*

class Globals {
	static String lineSeparator = System.getProperty("line.separator");
	static int numberOfCalculationThreads = Runtime.getRuntime().availableProcessors();
}

class Parser {

	def datasetDir;

	Parser(def datasetDir) {
		this.datasetDir = datasetDir;
	}

	Map loadShowVertices(Graph g) {
		def allShowIds = [] as Set;
		
		// load titles
		def titles = [:];
		new File(datasetDir, 'titles.csv').eachLine {def line ->
	  		def components = line.split(',',2);
	  		allShowIds.add(components[0]);
			titles.put(components[0],components[1]);
		}
		//titles.each { def entry -> println "$entry.key: $entry.value"}

		// load descriptions
		def descriptions = [:];
		new File(datasetDir, 'descriptions.csv').eachLine {def line ->
	  		def components = line.split(',',2);
	  		allShowIds.add(components[0]);
			descriptions.put(components[0], components[1]);
		}
		//descriptions.each { def entry -> println "$entry.key: $entry.value"}
		
		// load attributes
		def attributes = [:];
		def header = [];
		def firstLine = true;
		new File(datasetDir, 'attributeMatrix/matrix.csv').eachLine {def line ->
			def components = line.split(',');
			if (firstLine) {
				header = components;
				firstLine = false;
			} else {			
				def showId = components[0];
				allShowIds.add(showId);
				def values = [];
	  			components[1..<components.size()].eachWithIndex{ att, index ->
	  				if (att.equals('1')) {
	  					values.add(header[index]);
	  				}
	  			}
	  			attributes.put(showId, values);
	  		}			
		}
		//println header;
		//println attributes;

		// create attribute vertices
		def attributeVertexMap = [:]
		header.each {attribute -> 
			def attVertex = g.addVertex(attribute,['type':'Attribute', 'attribute':attribute]);
			attributeVertexMap.put(attribute, attVertex);
		}

		// create show vertices and link to attribute vertices
		allShowIds = allShowIds as Set;
		def showVertexMap = [:];
		allShowIds.each {showId -> 
			def showVertex = g.addVertex(showId, ['type':'Show', 'BBC_id':showId, 'title':titles.get(showId), 'description':descriptions.get(showId)]);
			showVertexMap.put(showId, showVertex);
			def showAtts = attributes.get(showId);
			showAtts.each {att ->
				g.addEdge(showVertex, attributeVertexMap.get(att), 'hasAttribute');
			}
		}
		
		//println showVertexMap;
		return showVertexMap;
	}

	void loadUserVertices(Graph g, Map showVertexMap) {
		// create user vertices and link to show vertices
		def userVertexMap = [:];
		def currentLine = 0;
		new File(datasetDir, 'training.csv').eachLine {def line ->
	  		def components = line.split(',');
	  		def uId = components[0];
	  		def showId = components[1];
	  		def showVertex = showVertexMap.get(showId);
	  		def userVertex = userVertexMap.get(uId);
	  		if (userVertex == null) {
	  			userVertex = g.addVertex(uId, ['type':'User', 'BBC_id':uId]);
	  			userVertexMap.put(uId, userVertex);
	  		}
	  		g.addEdge(userVertex, showVertex, 'watched');
	  		if (currentLine % 10000 == 0) {
	  			println 'Number of processed lines of training file: ' + currentLine;
	  		}
	  		currentLine++;
		}
	}

	Set getTrainingUserIds() {
		def uIds = [];
		new File(datasetDir, 'training.csv').eachLine {def line ->
	  		def components = line.split(',');
	  		def uId = components[0];
	  		uIds.add(uId);
		}
		return uIds as Set;
	}
}

class SimpleCollaborativeFilteringRecommendationStrategy {

	List recommendShowsToUser(Vertex user){
		// get all shows watched by users that also watched a show of the current user in random order
		return user.as('currentUser').out('watched').in('watched').except('currentUser').out('watched').unique().toList();
	}
}

class RankedCollaborativeFilteringRecommendationStrategy {

	List recommendShowsToUser(Vertex user){
		// find all path from current user to new shows in one other user distance, count number of paths to new show, order descending by counts 
		def showsWatchedByCurrentUser = [];
		def res = user.out('watched').aggregate(showsWatchedByCurrentUser).in('watched').out('watched').except(showsWatchedByCurrentUser).groupCount.cap.orderMap(T.decr).toList();
		return res;
	}
}

class Recommender {

	def graph;
	def parser;
	def rootOuputDir;
	def strategy = new RankedCollaborativeFilteringRecommendationStrategy(); // TODO: Select your favourite strategy

	Recommender(def loadLoc, def rootOuputDir, def saveLoc){
		def storage = TinkerStorageFactory.getInstance().getTinkerStorage(TinkerGraph.FileType.JAVA);
		if (loadLoc == null) {
			throw new IllegalArgumentException("No dataset / serialized graph location specified")
		}
		def loadFile = new File (loadLoc);
		if (loadFile.getName().equals('tinkergraph.dat')) {
			println "-- Start loading serialized graph from $loadLoc --"
			this.graph = storage.load(loadFile.getParent());
			println "-- Serialized graph successfully loaded --"
		} else {
			this.graph = new TinkerGraph();
			println "-- New in-memory graph created: $graph --"
			println "-- Start loading graph from dataset --"
			this.parser = new Parser(loadFile);
			loadData();
			println "-- Loading graph from dataset completed --"
		}
		if (saveLoc != null) {
			saveLocFileP = new File(saveLoc);
			def date = new Date();
			def ts = date.format('yyyy-MM-dd_HH-mm-ss');
			def dirName = 'serialized-graph_' + ts;
			saveLocFileC = new File(saveLocFileP, dirName)
			println "-- Start serializing graph to $saveLocFileC --"
			storage.save(this.graph, saveLocFileC.getAbsolutePath());
			println "-- Graph successfully serialized --"
		}
		this.rootOuputDir = new File(rootOuputDir);
	}

	void process(def uIds) {
		try{
			if (uIds == null) {
				// get all user ids in graph if no user ids provided
				if (parser != null) {
					uIds = parser.getTrainingUserIds();
				} else {
					uIds = getUserIdsInGraph();
				}
			}
			// def uIds = ['35211', '603245', '135588', '7369', '540624', '123274']; // FIXME for quick tests; remove this!
			def numOfUsers = uIds.size();
			println "Calculating recommendations for $numOfUsers users";
			def duration = System.currentTimeMillis()  
			def recommendations = recommend(uIds);
			duration = (System.currentTimeMillis()  - duration) / 1000.0;
			println "Calculating recommendations for $numOfUsers users took $duration seconds (${duration/numOfUsers} s per user).";
			println 'Application terminated without exceptions.';
		} catch(Exception e){
			println e.printStackTrace();
			println 'Application terminated prematurely!';
			System.exit(-1);
		}
	}

	void loadData(){
		println '-- Start loading datataset into graph db --';
		graph.createKeyIndex('BBC_id',Vertex.class);
		def batchGraph = BatchGraph.wrap(graph);
		println '-- TinkerGrap wrapped into BatchGraph --';
		def showVertexMap = parser.loadShowVertices(batchGraph);
		println '-- Show vertices loaded --';
		parser.loadUserVertices(batchGraph, showVertexMap);
		println '-- User vertices loaded --';
		batchGraph.stopTransaction(TransactionalGraph.Conclusion.SUCCESS)
		println '-- Graph created and all transactions committed --';
		// printGraphStats(graph);
		println '-- Datataset successfully loaded into graph db --';
	}

	void printGraphStats(Graph g) {
		println '-- Some basic graph properties --';
		println 'Number of vertices: ' + g.V.count();
		// println 'Number of edges: ' + g.E.count(); takes too much time
		println 'Number of vertices of type show: ' + g.V('type','Show').count();
		println 'Number of vertices of type user: ' + g.V('type','User').count();
		println 'Number of vertices of type attribute: ' + g.V('type','Attribute').count();
		println '---------------------------------';
	}

	void recommend(def uIds) {
		println '-- Start recommendation calculation --';
		def runOutDir = createRunOutputDir();
		
		def totalUserCount = uIds.size();
		def pool = Executors.newFixedThreadPool(Globals.numberOfCalculationThreads);
		def futures = [];
		def defer = { c -> futures.add(pool.submit(c as Callable)) }
		def calculate = {l_uId, l_userCount -> 
			println "-- Start recommendation calculation for user $l_uId (${l_userCount}/${totalUserCount}) --";
			def userVertex = getVertexById(l_uId);
			def showsRankedForUser = strategy.recommendShowsToUser(userVertex);
			def recomString = getRecommsString(userVertex, showsRankedForUser);
			// println recomString; // print recommendations on stdout
			writeRecommStringToFile(recomString, l_uId, runOutDir); // print recommendations to file in output dir
			println "-- Recommendation calculation for user $l_uId completed (${l_userCount}/${totalUserCount}) --";
			writeOutput(showsRankedForUser, l_uId, runOutDir, l_userCount, totalUserCount);
		}

		uIds.eachWithIndex { uId, i ->
			defer{ calculate(uId, i + 1) };
		}
		pool.shutdown();
		futures.each { f ->
			try {
				f.get();
			} catch(Exception e) {
				throw new RuntimeException("Exception in ExecutorService", e);
			}
		}
		println '-- Recommendation calculation completed --';
	}

	String getRecommsString(def user, def res) {
		def showsWatchedByCurrentUser = user.out('watched').toList();
		def sb = new StringBuilder();
		sb.append("### Watched shows (${showsWatchedByCurrentUser.size()}): ###");		
		showsWatchedByCurrentUser.each{show ->
			def description = shortenDescription(show, 100);
			sb.append(Globals.lineSeparator);
			sb.append("${show.BBC_id} - ${show.title} - ${description}");
		}
		sb.append(Globals.lineSeparator);
		sb.append("### Recommendations (${res.size()}, best first): ###");
		res.eachWithIndex(){show , index ->
			def description = shortenDescription(show, 100);
			sb.append(Globals.lineSeparator);
			sb.append("${index + 1} - ${show.BBC_id} - ${show.title} - ${description}");
		}
		return sb.toString();
	}

	String shortenDescription(def showVertex, def length) {
		def description = showVertex.description;
		if (description.size() > length) {
			return description[0..length] + "...";
		}
		return description;
	}

	Vertex getVertexById(String bbcId) {
		return graph.V('BBC_id',bbcId).next();
	}

	List getUserIdsInGraph() {
		return graph.V.filter{it.'type' == 'User'}.BBC_id.toList();
	}

	File createRunOutputDir(){
		// compile filename
		def date = new Date();
		def ts = date.format('yyyy-MM-dd_HH-mm-ss');
		def fileName = 'GraphRecomOutput_' + strategy.getClass().getSimpleName() + '_' + ts;
		
		// crete output directory for run
		def runOutDir = new File(rootOuputDir, fileName);
		runOutDir.mkdirs();
		return runOutDir;
	}

	void writeRecommStringToFile(def string, def uId, def runOutDir) {
		def outFile = new File(runOutDir, uId + '-description.txt');
		outFile << string;
	}

	void writeOutput(def result, def uId, def runOutDir, def userCount, def totalUserCount) {
		println "-- Start writing recommendations for user $uId (${userCount}/${totalUserCount}) --";
		def outFile = new File(runOutDir, uId + '.csv');

		// add header
		outFile << 'user_id, show_id, rank, max_rank';
		outFile << Globals.lineSeparator;

		// write results
		def count = 1;
		def maxCount = result.size();
		result.each{ entry ->
			outFile << (uId);
			outFile << (',');
			outFile << (entry.BBC_id);
			outFile << (',');
			outFile << (count);
			outFile << (',');
			outFile << (maxCount);
			outFile << (Globals.lineSeparator);
			count++;
		}
		println "-- Writing recommendations for user $uId completed (${userCount}/${totalUserCount}) --";
	}

}

println "Your input arguments: $args"

// command line args processing
cli = new CliBuilder(usage: 'main.groovy [-p <directory-to-persist-graph>] [-u <comma-separated-list-of-user-ids>] <path-to-dir-with-dataset-or-file-of-serialized-graph> <output-directory>')
cli.with{
	h longOpt: 'help', 'Show usage information'
	p longOpt: 'persist-graph', args: 1, argName: 'path', 'Persist the in-memory graph after loading of the dataset to disk at location "path"'
	u longOpt: 'users-to-recommend', args: 1, argName: 'users', 'Comma separated list of user ids (need to be contained in the graph) for which we want to generate recommendations'
}
def options = cli.parse(args)
if (!options || options.h) {
	cli.usage()
	System.exit(0);
}

def inputOutput = options.arguments();

recommender = new Recommender(inputOutput[0], inputOutput[1], options.p);
recommender.process(options.u.split(','));
