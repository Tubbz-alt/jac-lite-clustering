package gov.pnnl.jac.geom;

import gov.pnnl.jac.task.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CancellationException;

import org.apache.log4j.Logger;

public class CoordinateIO {

	private static final Logger logger = Logger.getLogger(CoordinateIO.class);
	
	public static CoordinateList loadCSV(File file, String nameForTuples, 
			CoordinateListFactory factory) throws IOException {
		try {
			return loadCSV(file, null, ",", 0, 0, nameForTuples, factory, null, null);
		} catch (CancellationException ce) {
			throw new IOException("loading canceled", ce);
		}
	}

	public static CoordinateList loadCSV(File file, String nameForTuples, 
			CoordinateListFactory factory, Cancelable cancelable) throws IOException, CancellationException {
		return loadCSV(file, null, ",", 0, 0, nameForTuples, factory, cancelable, null);
	}
	
	public static CoordinateList loadCSV(File file, String nameForTuples, 
			CoordinateListFactory factory, Cancelable cancelable, ProgressHandler ph) throws IOException, CancellationException {
		return loadCSV(file, null, ",", 0, 0, nameForTuples, factory, cancelable, ph);
	}

	public static CoordinateList loadCSV(
			File file, String charSet, 
			String delimiter, 
			int startColumn, int columnCount,
			String nameForTuples,
			CoordinateListFactory factory) throws IOException {
		try {
			return loadCSV(file, charSet, delimiter, startColumn, columnCount, nameForTuples, factory, null, null);
		} catch (CancellationException ce) {
			throw new IOException("loading canceled", ce);
		}
	}
		
	/**
	 * Loads numeric data contained in a csv file into a CoordinateList
	 * 
	 * @param file the file containing the data.
	 * @param charSet the character set of the file. If null, the default character set is used.
	 * @param delimiter the delimiter, which is a comma by definition for csv files.  However, you can read
	 *                  tab-delimited files too, but passing in a tab.
	 * @param startColumn the starting column in case some columns at the beginning of each row should be ignored.
	 *                    For example, some csv files may have a row number or id in the 0th column.
	 * @param columnCount the number of columns.
	 * @param nameForTuples the name to assign to the CoordinateList within its factory.
	 * @param factory the CoordinateListFactory, which will manage the CoordinateList.
	 * @param cancelable if non-null, this will be checked periodically to see if loading the data should be
	 *                   cancelled.  If null, it is ignored.
	 *                   
	 * @return a CoordinateList containing the data.
	 * 
	 * @throws IOException if some kind if IO error occurs.
	 * 
	 * @throws CancellationException if loading is cancelled.
	 */
	public static CoordinateList loadCSV(
			File file, 
			String charSet, 
			String delimiter, 
			int startColumn, 
			int columnCount,
			String nameForTuples,
			CoordinateListFactory factory,
			Cancelable cancelable,
			ProgressHandler ph) throws IOException, CancellationException {

		if (charSet == null) {
			charSet = Charset.defaultCharset().name();
		}

		// Parse out information on the data in the file such as the number of rows and
		// the columns containing numeric data.
		CSVInfo csvInfo = parseCSVInfo(file, charSet, startColumn, delimiter, cancelable);	
		
		// When a bit is set, each row has numeric data for that column.
		BitSet colBits = csvInfo.getColumnBits();

		// The first row that has numeric data.  The startRow might not be 0 if the
		// first row contains headings.
		final int startRow = csvInfo.getStartRow();
		
		final int rows = csvInfo.getRowCount();
		
		// The number of columns is the number of set bits.
		// Don't have to worry about startColumn.  Bits for columns < startColumn won't be set.
		final int cols = colBits.cardinality();

		// Have to have some data.
		if (rows == 0 || cols == 0) {
			throw new IOException(String.format("no data found: rows = %d, columns = %d", rows, cols));
		}

		// Have the factory create an empty CoordinateList to hold the data.
		// In order for the factory to know what kind of CoordinateList to create, it
		// has to know the number of rows and columns.
		CoordinateList tuples = factory.createCoordinateList(nameForTuples, cols, rows);
		
		// Temporary buffer
		double[] buffer = new double[cols];

		BufferedReader br = null;
		boolean ok = false;

		try {

			br = new BufferedReader(new InputStreamReader(new FileInputStream(file), charSet));
			String line;
			int lineNum = -1;
			int row = 0;

			if (ph != null) {
				ph.subsection(1.0, rows);
				ph.postBegin();
			}
			
			while((line = br.readLine()) != null) {

				if (cancelable != null && cancelable.isCanceled()) {
					throw new CancellationException();
				}

				line = line.trim();

				if (line.length() > 0) {

					lineNum++;

					if (lineNum >= startRow) {

						try {

							StringTokenizer tokenizer = new StringTokenizer(line, delimiter);
							int tokenCount = tokenizer.countTokens();

							int colIndex = 0;
							for (int i=0; i<tokenCount; i++) {
								String token = tokenizer.nextToken();
								if (colBits.get(i)) {
									buffer[colIndex++] = Double.parseDouble(token);
								}
							}

							tuples.setCoordinates(row, buffer);
							
							if (ph != null) {
								ph.postStep();
							}

						} catch (NoSuchElementException nsee) {

							throw new IOException("too few columns on row " + row);

						} catch (NumberFormatException nfe) {

							throw new IOException(String.format("unparseable element on row %d: %s",
									lineNum, line));
							
						}

						row++;
					}
				}

			}

			ok = row == rows;

		} finally {

			// Be sure that the file is closed properly.
			if (br != null) {
				try {
					br.close();
				} catch (IOException ioe) {
					logger.error(ioe);
				}
			}

			if (ph != null) {
				ph.postEnd();
			}
		}

		return tuples;
	}
	
	/**
	 * Parse a csv file to determine the number of rows and which columns contain
	 * numeric data.  
	 * 
	 * @param file
	 * @param charSet
	 * @param startColumn
	 * @param delimiter
	 * @param cancelable
	 * @return
	 * @throws IOException
	 * @throws CancellationException
	 */
	private static CSVInfo parseCSVInfo(File file, String charSet, 
			int startColumn, String delimiter, Cancelable cancelable) 
					throws IOException, CancellationException {
		
		int startRow = -1;
		int lineNum = -1;
		BitSet columnBits = null;
		int expectedTokenCount = -1;
		
		BufferedReader br = null;
		
		try {
			
			if (charSet == null) {
				charSet = Charset.defaultCharset().name();
			}
			
			br = new BufferedReader(new InputStreamReader(new FileInputStream(file), charSet));
			String line;
		
			while((line = br.readLine()) != null) {
				
				if (cancelable != null && cancelable.isCanceled()) {
					throw new CancellationException();
				}

				line = line.trim();
				
				if (line.length() > 0) {
					
					// The 0-based index of nonblank lines.
					lineNum++;
					
					StringTokenizer tokenizer = new StringTokenizer(line, delimiter);
					int tokenCount = tokenizer.countTokens();
				
					if (startRow < 0) {
					
						if (tokenCount > startColumn) {

							BitSet bits = new BitSet(tokenCount);

							// Throw away everything before the startColumn
							for (int i=0; i<startColumn; i++) {
								tokenizer.nextToken();
							}
						
							for (int i=startColumn; i<tokenCount; i++) {
								try {
									Double.parseDouble(tokenizer.nextToken());
									// Flag the column as containing a parseable double.
									bits.set(i);
								} catch (NumberFormatException nfe) {
									// Don't worry about it.  
								}
							}
						
							if (bits.cardinality() > 0) {
								startRow = lineNum;
								columnBits = bits;
								expectedTokenCount = tokenCount;
							}
						}
						
					} else {
						
						if (tokenCount != expectedTokenCount) {
							throw new IOException(String.format(
									"incorrect number of entries on line %d: %d expected, found %d", 
									(lineNum+1), expectedTokenCount, tokenCount));
						}
						
					}
				
				}
			}
			
		} finally {
			
			if (br != null) {
				try {
					br.close();
				} catch (IOException ioe) {
				}
			}
			
		}
		
		if (startRow < 0) {
			throw new IOException("no numeric data found in " + file.getName());
		}
		
		return new CSVInfo(startRow, lineNum - startRow + 1, columnBits);
	}

	/**
	 * Saves data from a CoordinateList to a csv file using default settings.
	 * 
	 * @param file
	 * @param tuples
	 * @throws IOException
	 */
	public static void saveCSV(File file, CoordinateList tuples) throws IOException {
		saveCSV(file, null, ",", null, tuples, null);
	}
	
	/**
	 * Saves the values from a CoordinateList to a csv file.
	 * 
	 * @param file the file to which to save the data.
	 * @param charSet the character set name to use. If null, the default character set is used.
	 * @param delimiter the delimiter to use.
	 * @param outputPattern a pattern such as &quot;%5.2f&quot; for formatting the numbers as
	 *                      they are written. If null, default formatting is used.
	 * @param tuples the CoordinateList containing the data.
	 * @param headers headers to write to the first row, if non-null.  If non-null this
	 *                must be the same length as the tuples.
	 *                
	 * @throws IOException
	 */
	public static void saveCSV(
			File file, String charSet, String delimiter, String outputPattern, CoordinateList tuples, String[] headers) throws IOException {
		
		if (charSet == null) {
			charSet = Charset.defaultCharset().name();
		}
		
		final int tupleLength = tuples.getDimensionCount();
		final int tupleCount = tuples.getCoordinateCount();
		
		if (headers != null && headers.length != tupleLength) {
			throw new IOException(String.format("number of headers != tuple length: %d != %d", headers.length, tupleLength));
		}
		
		double[] buffer = new double[tupleLength];
		
		PrintWriter pw = null;
		
		try {
			pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charSet)));
			if (headers != null) {
				for (int i=0; i<tupleLength; i++) {
					if (i > 0) pw.print(delimiter);
					pw.print(headers[i]);
				}
				pw.println();
			}
			for (int i=0; i<tupleCount; i++) {
				tuples.getCoordinates(i, buffer);
				for (int j=0; j<tupleLength; j++) {
					if (j > 0) pw.print(delimiter);
					if (outputPattern != null) {
						pw.printf(outputPattern, buffer[j]);
					} else {
						pw.print(buffer[j]);
					}
				}
				pw.println();
			}
		} finally {
			if (pw != null) {
				pw.close();
			}
		}
		
	}

	// Encapsulates information on the contents of a csv file
	//
	static class CSVInfo {
		
		// The starting row.
		private int startRow;
		// The number of rows.
		private int rowCount;
		// Set bits indicate the columns containing numeric data.
		private BitSet columnBits;
		
		CSVInfo(int startRow, int rowCount, BitSet columnBits) {
			this.startRow = startRow;
			this.rowCount = rowCount;
			this.columnBits = columnBits;
		}
		
		public int getStartRow() {
			return startRow;
		}
		
		public int getRowCount() {
			return rowCount;
		}
		
		public BitSet getColumnBits() {
			return columnBits;
		}
	}

}
