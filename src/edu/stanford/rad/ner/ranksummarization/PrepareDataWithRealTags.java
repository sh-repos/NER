package edu.stanford.rad.ner.ranksummarization;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.rad.ner.util.GenNegEx;
import edu.stanford.rad.ner.util.KWAnnotation;
import edu.stanford.rad.ner.util.ReadKWAnnotations;
import edu.stanford.rad.ner.util.Stemmer;

public class PrepareDataWithRealTags {

	public static void main(String[] args) throws FileNotFoundException,IOException {
		long startTime = System.currentTimeMillis();
		GenNegEx g = new GenNegEx(true);
		Stemmer stemmer = new Stemmer();
		final File folder = new File("files/corpus");
		for (final File fileEntry : folder.listFiles()) {
			if (!fileEntry.isDirectory()) {
				String fileName = fileEntry.getName();
				String filepath = fileEntry.getPath();
				String XmlfileName = "files/annotation/" + fileName + ".knowtator.xml";
				File Xmlfile = new File(XmlfileName);
				if (!Xmlfile.exists()) {
					System.out.println(XmlfileName + " not found! " + fileName + " is skipped.");
					continue;
				}
				List<String> allReports = new ArrayList<String>();
				System.out.println("Working on " + fileName + "...");
				Scanner scanner = new Scanner(new File(filepath), "UTF-8");
				String text = scanner.useDelimiter("\\Z").next();
				scanner.close();
				text = text.replaceAll("\r\n", "\n");

				ReadKWAnnotations readKWAnnotations = new ReadKWAnnotations();
				Map<String, KWAnnotation> kwAnnotationMap = readKWAnnotations.read(XmlfileName);
				List<KWAnnotation> kwAnnotationList = new ArrayList<KWAnnotation>(kwAnnotationMap.values());
				Collections.sort(kwAnnotationList);
				ListIterator<KWAnnotation> it = kwAnnotationList.listIterator();
				int aEnd = -1;
				KWAnnotation kw = null;

				Properties props = new Properties();
				props.put("annotators", "tokenize, ssplit, pos, lemma, parse"); // ,ner,parse,dcoref
				props.put("ssplit.newlineIsSentenceBreak", "always");
				StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
				Annotation document = new Annotation(text);
				pipeline.annotate(document);

				List<CoreMap> sentences = document.get(SentencesAnnotation.class);
				StringBuilder report = new StringBuilder();

				for (CoreMap sentence : sentences) {
					String scope = g.negScope(cleans(sentence.toString()));
					int nStart = -1, nEnd = -1, counter = 0;
				    if (!scope.equals("-1") && !scope.equals("-2"))
				    {
				    	String[] number = scope.split("\\s+");
				    	nStart = Integer.valueOf(number[0]);
				    	nEnd = Integer.valueOf(number[2]);
				    }
				    
					Tree tree = sentence.get(TreeAnnotation.class);
					List<IntPair> spans = new ArrayList<IntPair>();
					tree.setSpans();
					//System.out.println(tree);
					for (Tree subtree : tree) {
						if (subtree.label().value().equals("NP")) {
							int maxHeight = tree.depth(subtree);
							boolean subset = false;
							for (int i = 1; i < maxHeight; ++i) {
								Tree ancestor = subtree.ancestor(i, tree);
								if (ancestor.label().value().equals("NP")) {
									subset = true;
									break;
								}
							}
							if (!subset) {
								spans.add(subtree.getSpan());
								//System.out.println(subtree.getLeaves());
							}

						}
					}
				    
					ListIterator<IntPair> spanIt = spans.listIterator();
					IntPair span = null;
					if (spanIt.hasNext()) {
						span = spanIt.next();
					}

					for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
						String word = token.get(TextAnnotation.class);
						if(word.equals("********************************************"))
						{
							allReports.add(report.toString());
							report = new StringBuilder();
							continue;
						}
						int index = token.index()-1;
						int start = token.beginPosition();
						int end = token.endPosition();
						String pos = token.get(PartOfSpeechAnnotation.class);
						
						String Stanfordlemma = token.get(LemmaAnnotation.class);
						char[] wordCharArray = Stanfordlemma.toLowerCase().toCharArray();
						stemmer.add(wordCharArray, wordCharArray.length);
						stemmer.stem();
						String lemma = stemmer.toString();
						
						if (span!=null && index > span.getTarget() && spanIt.hasNext()) {
							span = spanIt.next();
						}
						
						String NP = "O"; 
						if (span != null) {
							if (span.getSource() == index) {
								NP = "B";
							} else if (span.getTarget() == index) {
								NP = "E";
							} else if (span.getSource() < index && index < span.getTarget()) {
								NP = "I";
							}
						}

						while (aEnd < start && it.hasNext()) {
							kw = it.next();
							aEnd = kw.end;
						}

						String mentionClass = "O";
						if (kw != null && kw.start <= start && kw.end >= end) {
							mentionClass = kw.mentionClass;
						}
						mentionClass = mentionClass.replaceAll(" ", "_");
						
						String negex = "P";
						if(nStart <= counter && counter <= nEnd){
							negex = "N";
						}
						if(isClean(word))
							counter++;
						
						report.append(word + "\t" + mentionClass + "\t" + pos+ "\t" + lemma + "\t" + negex + "\t" + NP + "\n");
					}
				}
				
				for(int i=0; i<allReports.size(); ++i) {
					int fileNumber = i+1;
					PrintWriter pw = new PrintWriter("files/summarizatonInput/sum_" + fileName.split("\\.")[0] + "_" + fileNumber +".tsv", "UTF-8");
					pw.print(allReports.get(i));
					pw.close();
				}
			
			}
		}
		
		long endTime   = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		System.out.println("Finshed in " + totalTime/1000.0 + " seconds");
	}
	
    // post: removes punctuations
    private static String cleans(String line) {
	line = line.toLowerCase();
	if (line.contains("\""))
	    line = line.replaceAll("\"", "");
	if (line.contains(","))
	    line = line.replaceAll(",", "");  
	if (line.contains("."))
	    line = line.replaceAll("\\.", "");
	if (line.contains(";"))
	    line = line.replaceAll(";", "");
	if (line.contains(":"))
	    line = line.replaceAll(":", "");
	return line;
    }
    
    private static boolean isClean(String token) {
		if (token.equals("\"") || token.equals(",") || token.equals(".") || token.equals(";") || token.equals(":")){
			return false;
		}
		else{
			return true;
		}
    }
}
