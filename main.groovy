// inspired by: http://markorodriguez.com/2011/09/22/a-graph-based-movie-recommender-engine/

class Globals {
	// TODO: Set your paths
   static File rootDir = new File('/Users/faber/Documents/Job-ifi/BBC/dataset/feb2014'); // The location with the input files
   static String databaseDir = '/Users/faber/Documents/Job-ifi/BBC/graphRecommender/database'; // Location where graph db will be located (maybe several Gigs)
   static File outDir = new File('/Users/faber/Documents/Job-ifi/BBC/graphRecommender/out'); // Output files will be located here
}

class Parser {
	Map loadShowVertices(Graph g) {
		def allShowIds = [] as Set;
		
		// load titles
		def titles = [:];
		new File(Globals.rootDir, 'titles.csv').eachLine {def line ->
	  		def components = line.split(',',2);
	  		allShowIds.add(components[0]);
			titles.put(components[0],components[1]);
		}
		//titles.each { def entry -> println "$entry.key: $entry.value"}

		// load descriptions
		def descriptions = [:];
		new File(Globals.rootDir, 'descriptions.csv').eachLine {def line ->
	  		def components = line.split(',',2);
	  		allShowIds.add(components[0]);
			descriptions.put(components[0], components[1]);
		}
		//descriptions.each { def entry -> println "$entry.key: $entry.value"}
		
		// load attributes
		def attributes = [:];
		def header = [];
		def firstLine = true;
		new File(Globals.rootDir, 'attributeMatrix/matrix.csv').eachLine {def line ->
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
		new File(Globals.rootDir, 'smallerDataset/training.csv').eachLine {def line ->
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
		new File(Globals.rootDir, 'smallerDataset/training.csv').eachLine {def line ->
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
		return user.as('currentUser').out('watched').in('watched').except('currentUser').out('watched').BBC_id.unique().toList();
	}
}

class RankedCollaborativeFilteringRecommendationStrategy {

	List recommendShowsToUser(Vertex user){

		// find all path from current user to new shows in one other user distance, count number of paths to new show, order descending by counts 
		def showsWatchedByCurrentUser = [];
		def res = user.out('watched').aggregate(showsWatchedByCurrentUser).in('watched').out('watched').except(showsWatchedByCurrentUser).groupCount.cap.orderMap(T.decr).toList();
		printRecomms(user, res); // comment out to get rid of prints
		return res.BBC_id;
	}

	void printRecomms(def user, def res) {
		println '-- Processing user ' + user.BBC_id + '--';
		println '### Shows watched by user: ###'
		def showsWatchedByCurrentUser = user.out('watched').toList();
		showsWatchedByCurrentUser.each{show ->
			println "${show.BBC_id} - ${show.title} - ${show.description}";
		}
		println '### Ordered recommendations to user: ###'
		res.each{show ->
			println "${show.BBC_id} - ${show.title} - ${show.description}";
		}
		println '-- Processing user ' + user.BBC_id + ' completed --';
	}
}

class DummyRecommendationStrategy {
	List recommendShowsToUser(Vertex user){
		return ['12313','wefewfew','addf12312'];
	}
}

class Recommender {

	def graph = TitanFactory.open(Globals.databaseDir);
	def parser = new Parser();
	def strategy = new RankedCollaborativeFilteringRecommendationStrategy(); // TODO: Select your favourite strategy

	void process() {
		// loadData(); // TODO: Uncomment to create graph, i.e. in first run
		def recommendations = recommend();
		writeOutput(recommendations);
	}

	void loadData(){
		graph.createKeyIndex('BBC_id',Vertex.class);
		def batchGraph = BatchGraph.wrap(graph);
		println '-- TitanGrap wrapped into BatchGraph --';
		def showVertexMap = parser.loadShowVertices(batchGraph);
		println '-- Show vertices loaded --';
		parser.loadUserVertices(batchGraph, showVertexMap);
		println '-- User vertices loaded --';
		batchGraph.stopTransaction(TransactionalGraph.Conclusion.SUCCESS)
		println '-- Graph created and all transactions committed --';
		// printGraphStats(graph);
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

	List recommend() {
		println '-- Start recommendation calculation --';
		def uIds = parser.getTrainingUserIds();
		//def uIds = ['100033']; // FIXME remove this!
		def results = [] // [userId, showId, rank, maxRank]
		uIds.each { uId ->
			def showsRankedForUser = strategy.recommendShowsToUser(getVertexById(uId));
			def count = 1;
			showsRankedForUser.each { rankedShow ->
				results.add([uId, rankedShow, count, showsRankedForUser.size()]);
				count++;
			}
		}
		println '-- Recommendation calculation completed --';
		return results;
	}

	Vertex getVertexById(String bbcId) {
		return graph.V('BBC_id',bbcId).next();
	}

	void writeOutput(List result) {
		println '-- Start writing output recommendations --';

		// compile filename
		def date = new Date();
		def ts = date.format('yyyy-MM-dd_HH-mm-ss');
		def fileName = 'GraphRecomOutput_' + strategy.getClass().getSimpleName() + '_' + ts + '.csv';
		def outFile = new File(Globals.outDir, fileName);
		def lineSep = System.getProperty("line.separator");
		
		// add header
		outFile << 'user_id, show_id, rank, max_rank';
		outFile << lineSep;

		// write results
		result.each{ entry ->
			outFile << (entry[0]);
			outFile << (',');
			outFile << (entry[1]);
			outFile << (',');
			outFile << (entry[2]);
			outFile << (',');
			outFile << (entry[3]);
			outFile << (lineSep);
		}
		println '-- Writing output completed --';
	}

}

recommender = new Recommender();
recommender.process();
