package gov.pnnl.jac.geom;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;

/**
 * <p><tt>DoubleMatrix2DCoordinateList</tt> is a wrapper class
 * with the purpose of making a <tt>cern.colt.matrix.DoubleMatrix2D</tt>
 * instance into a <tt>CoordinateList</tt>.</p>
 * 
 * @author R. Scarberry
 *
 */
public class DoubleMatrix2DCoordinateList extends AbstractCoordinateList {

	// The matrix containing the data.
	private DoubleMatrix2D mMatrix;

	/**
	 * Constructor.
	 * @param matrix - the wrapped matrix containing the coordate data.
	 */
	public DoubleMatrix2DCoordinateList(DoubleMatrix2D matrix) {
		this.mCount = matrix.rows();
		this.mDim = matrix.columns();
		this.mMatrix = matrix;
	}
	
	/**
	 * Constructor
	 * @param data from which a backing DoubleMatrix2D is populated.
	 */
	public DoubleMatrix2DCoordinateList(double[][] data) {
	    this.mMatrix = DoubleFactory2D.dense.make(data);
	    this.mCount = this.mMatrix.rows();
	    this.mDim = this.mMatrix.columns();	
	}

    /**
     * Identical to <code>getCoordinate(ndx, dim)</code>, but
     * bounds checking is not performed on the arguments.  This
     * method is mandated by the interface, so other methods  
     * can retrieve coordinates in loops without having 
     * redundant bounds checking performed on every iteration.  
     * Do not call this method directly unless you are sure the
     * arguments are in range.  If they are not, the behavior is
     * determined by the implementation class.  
     * @param ndx - the coordinate index which must be in the range
     *   <code>[0 - getCoordinateCount()-1]</code>.
     * @param dim - the dimension for the value, which must be
     *   in the range <code>[0 - getDimensions() - 1]</code>.
     * @return - the value.
     */
	public double getCoordinateQuick(int ndx, int dim) {
		return mMatrix.getQuick(ndx, dim);
	}

    /**
     * Retrieve the coordinate values for the coordinate with
     * the specified index.  
     * @param ndx - the coordinate index which must be in the range
     *   <code>[0 - getCoordinateCount()-1]</code>.
     * @param coords - an array to hold the returned coordinates. 
     *   If non-null, must be of length <code>getDimensions()</code>.
     *   If null, a new array is allocated and returned with the values.
     * @return - the array containing the values, which will be the
     *   same as the second argument if that argument is non-null.
     * @throws IndexOutOfBoundsException - if <code>ndx</code> 
     *   is not in the valid range.
     * @throws IllegalArgumentException - if the array passed in is
     *   non-null but of incorrect length.  
     */
	public double[] getCoordinates(int ndx, double[] coords) {
		checkIndex(ndx);
		final int dim = this.mDim;
		double[] c = null;
		if (coords != null) {
			checkDimensions(coords.length);
			c = coords;
		} else {
			c = new double[dim];
		}
		for (int i = 0; i < dim; i++) {
			c[i] = mMatrix.getQuick(ndx, i);
		}
		return c;
	}

    /**
     * Identical to <code>setCoordinate(ndx, dim, coord)</code>, but
     * bounds checking is not performed on the arguments.  This
     * method is mandated by the interface, so other methods  
     * can quickly set values in loops without having 
     * redundant bounds checking performed on every iteration.  
     * Do not call this method directly unless you are sure the
     * arguments are in range.  If they are not, the behavior is
     * determined by the implementation class.  
     * @param ndx the index of the coordinate.
     * @param dim the dimension to be set.
     * @param coord the value to be applied.
     * 
     * @throws IndexOutOfBoundsException if either ndx or dim is out of range.
     */
	public void setCoordinateQuick(int ndx, int dim, double coord) {
		mMatrix.setQuick(ndx, dim, coord);
	}

    /**
     * Set the coordinate values for the coordinate with the
     * specified index.
     * @param ndx - the coordinate index which must be in the range
     *   <code>[0 - getCoordinateCount()-1]</code>.
     * @param coords - the coordinate values.
     * @throws IndexOutOfBoundsException - if <code>ndx</code> is out of range.
     * @throws IllegalArgumentException - if <code>coords</code> is not
     *   of length <code>getDimensions()</code>.
     */
	public void setCoordinates(int ndx, double[] coords) {
		checkIndex(ndx);
		checkDimensions(coords.length);
		final int dim = this.mDim;
		for (int i = 0; i < dim; i++) {
			mMatrix.setQuick(ndx, i, coords[i]);
		}
	}
}
