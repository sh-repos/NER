package edu.stanford.rad.ner.ranksummarization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;

public class NERTagging {

	public static void main(String[] args) throws Exception {
		long startTime = System.currentTimeMillis();
		String modelType = "CRF"; // CRF,CMM
		PrintStream defaultOut = System.out;
		String model = "files/model/modelCRF/ner_CRF_0.ser.gz";

		final File Datafolder = new File("files/summarizatonInput");
		for (final File fileEntry : Datafolder.listFiles()) {
			if (!fileEntry.isDirectory() && !fileEntry.getName().startsWith(".")) {
				String inputFileName = fileEntry.getName();
				String inputFilepath = fileEntry.getPath();
				System.setOut(defaultOut);
				System.out.println("Working on " + inputFileName + "...");
				
				File outputFile = new File("files/summarizatonTags/tagged_" + inputFileName);  
				FileOutputStream fis = new FileOutputStream(outputFile.getPath());  
				PrintStream out = new PrintStream(fis);  
				System.setOut(out);

				String[] modelArgs = { "-loadClassifier", model, "-testFile", inputFilepath };

				if (modelType.equals("CRF"))
					CRFClassifier.main(modelArgs);
				else if (modelType.equals("CMM"))
					CMMClassifier.main(modelArgs);
			}
		}
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		System.err.println("Finshed in " + totalTime / 1000.0 + " seconds");
	}
}
