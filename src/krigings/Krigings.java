/* This file is part of JGrasstools (http://www.jgrasstools.org)
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * JGrasstools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package krigings;

import static org.jgrasstools.gears.libs.modules.JGTConstants.isNovalue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Documentation;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Name;
import oms3.annotations.Out;
import oms3.annotations.Status;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.jgrasstools.gears.libs.exceptions.ModelsIllegalargumentException;
import org.jgrasstools.gears.libs.exceptions.ModelsRuntimeException;
import org.jgrasstools.gears.libs.modules.JGTConstants;
import org.jgrasstools.gears.libs.modules.JGTModel;
import org.jgrasstools.gears.libs.modules.ModelsEngine;
import org.jgrasstools.gears.libs.monitor.IJGTProgressMonitor;
import org.jgrasstools.gears.libs.monitor.LogProgressMonitor;
import org.jgrasstools.gears.utils.math.matrixes.ColumnVector;
import org.jgrasstools.gears.utils.math.matrixes.LinearSystem;
import org.jgrasstools.gears.utils.sorting.QuickSortAlgorithm;
import org.jgrasstools.hortonmachine.i18n.HortonMessageHandler;
import org.opengis.feature.simple.SimpleFeature;

import krigings.Model;
import krigings.SimpleModelFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

@Description("Ordinary kriging algorithm.")
@Documentation("Kriging.html")
@Author(name = "Giuseppe Formetta, Daniele Andreis, Silvia Franceschi, Andrea Antonello", contact = "http://www.hydrologis.com,  http://www.ing.unitn.it/dica/hp/?user=rigon")
@Keywords("Kriging, Hydrology")
@Label(JGTConstants.STATISTICS)
@Name("kriging")
@Status(Status.EXPERIMENTAL)
@License("General Public License Version 3 (GPLv3)")
@SuppressWarnings("nls")
public class Krigings extends JGTModel {

	@Description("The vector of the measurement point, containing the position of the stations.")
	@In
	public SimpleFeatureCollection inStations = null;

	@Description("The field of the vector of stations, defining the id.")
	@In
	public String fStationsid = null;

	@Description("The field of the vector of stations, defining the elevation.")
	@In
	public String fStationsZ = null;
	
	@Description("The type of theoretical semivariogram: 0 = Gaussian; 1 = Exponential.")
	@In
	public String pSemivariogramType = null;

	@Description("The file with the measured data, to be interpolated.")
	@In
	public HashMap<Integer, double[]> inData = null;

	@Description("The vector of the points in which the data have to be interpolated.")
	@In
	public SimpleFeatureCollection inInterpolate = null;

	@Description("The field of the interpolated vector points, defining the id.")
	@In
	public String fInterpolateid = null;

	@Description("The field of the interpolated vector points, defining the elevation.")
	@In
	public String fPointZ = null;

	@Description("Switch for detrended mode.")
	@In
	public boolean doDetrended = false;

	@Description("Switch for detrended mode.")
	@In
	public double maxdist;

	@Description("The threshold on correlation coefficient for the trend in detrendend mode.")
	@In
	public double thresholdCorrelation;

	@Description("The interpolation mode (0 = interpolate on irregular grid, 1 = interpolate on regular grid).")
	@In
	public int pMode = 0;

	/**
	 * The integral scale, this is necessary to calculate the variogram if the
	 * program use {@link Kriging2.variogram(rx,ry,rz)}.
	 */
	@Description("The integral scale.")
	@In
	public double[] pIntegralscale = null;

	/**
	 * Variance of the measure field.
	 */
	@Description("The variance.")
	@In
	public double pVariance = 0;

	/**
	 * The logarithm selector, if it's true then the models runs with the log of
	 * the data.
	 */
	@Description("Switch for logaritmic run selection.")
	@In
	public boolean doLogarithmic = false;

	@Description("The collection of the points in which the data needs to be interpolated.")
	@In
	public GridGeometry2D inInterpolationGrid = null;

	@Description("The collection of the points in which the data needs to be interpolated.")
	@In
	public GridCoverage2D inGridCoverage2D = null;

	@Description("The progress monitor.")
	@In
	public IJGTProgressMonitor pm = new LogProgressMonitor();
	
	@Description("The semivariogram mode: 0=Integral scale; 1=classical semivariogram (sill range nugget).")
	@In
	public int defaultVariogramMode = 0;

	@Description("Include zeros in computations (default is true).")
	@In
	public boolean doIncludezero = true;

	@Description("Do local trend? (default is false).")
	@In
	public boolean plocalTrend = false;

	@Description("The range if the models runs with the gaussian variogram.")
	@In
	public double pA;

	@Description("The sill if the models runs with the gaussian variogram.")
	@In
	public double pS;

	@Description("The field of the interpolated vector points, defining the id.")
	@In
	public int inNumCloserStations;

	@Description("Is the nugget if the models runs with the gaussian variogram.")
	@In
	public double pNug;

	@Description("Is the nugget if the models runs with the gaussian variogram.")
	@Out
	public double[] outSemivarinces;
	@Description("Is the nugget if the models runs with the gaussian variogram.")
	@Out
	public double[] outDistances;
	@Description("Is the nugget if the models runs with the gaussian variogram.")
	@Out
	public double[] inSemivarinces;
	@Description("Is the nugget if the models runs with the gaussian variogram.")
	@Out
	public double[] inDistances;
	@Description("Is the nugget if the models runs with the gaussian variogram.")
	@In
	public boolean constrainGT0;

	@Description("The interpolated gridded data (for mode 2 and 3.")
	@Out
	public GridCoverage2D outGrid = null;

	@Description("The interpolated data (for mode 0 and 1).")
	@Out
	public HashMap<Integer, double[]> outData = null;

	/**
	 * A tolerance.
	 */
	private static final double TOLL = 1.0d * 10E-8;

	private HortonMessageHandler msg = HortonMessageHandler.getInstance();
	
	Model modelVGM;



	/**
	 * Executing ordinary kriging.
	 * <p>
	 * <li>Verify if the parameters are correct.
	 * <li>Calculating the matrix of the covariance (a).
	 * <li>For each point to interpolated, evalutate the know term vector (b)
	 * and solve the system (a x)=b where x is the weight.
	 * </p>
	 * 
	 * @throws SchemaException
	 */

	@Execute
	public void executeKriging() throws Exception {
		if (inGridCoverage2D != null) {
			inInterpolationGrid = inGridCoverage2D.getGridGeometry();
		}
		verifyInput();

		List<Double> xStationList = new ArrayList<Double>();
		List<Double> yStationList = new ArrayList<Double>();
		List<Double> zStationList = new ArrayList<Double>();
		List<Double> hStationList = new ArrayList<Double>();
		List<Integer> idStationList = new ArrayList<Integer>();

		/*
		 * counter for the number of station with measured value !=0.
		 */
		int n1 = 0;
		/*
		 * Store the station coordinates and measured data in the array.
		 */

		FeatureIterator<SimpleFeature> stationsIter = inStations.features();
		try {
			while (stationsIter.hasNext()) {
				SimpleFeature feature = stationsIter.next();
				int id = ((Number) feature.getAttribute(fStationsid))
						.intValue();
				double z = 0;
				if (fStationsZ != null) {
					try {
						z = ((Number) feature.getAttribute(fStationsZ))
								.doubleValue();
					} catch (NullPointerException e) {
						pm.errorMessage(msg.message("kriging.noStationZ"));
						throw new Exception(msg.message("kriging.noStationZ"));

					}
				}
				Coordinate coordinate = ((Geometry) feature
						.getDefaultGeometry()).getCentroid().getCoordinate();
				double[] h = inData.get(id);
				if (h == null || isNovalue(h[0])) {
					/*
					 * skip data for non existing stations.
					 * Also skip novalues.
					 */
					continue;
				}
				if (defaultVariogramMode == 0) {
					if (doIncludezero) {
						if (Math.abs(h[0]) >= 0.0) { // TOLL
							xStationList.add(coordinate.x);
							yStationList.add(coordinate.y);
							zStationList.add(z);
							hStationList.add(h[0]);
							idStationList.add(id);
							n1 = n1 + 1;
						}
					} else {
						if (Math.abs(h[0]) > 0.0) { // TOLL
							xStationList.add(coordinate.x);
							yStationList.add(coordinate.y);
							zStationList.add(z);
							hStationList.add(h[0]);
							idStationList.add(id);

							n1 = n1 + 1;
						}
					}
				} else if (defaultVariogramMode == 1) {
					if (doIncludezero) {
						if (Math.abs(h[0]) >= 0.0) { // TOLL
							xStationList.add(round(coordinate.x, 3));
							yStationList.add(round(coordinate.y, 3));
							zStationList.add(z);
							hStationList.add(h[0]);
							idStationList.add(id);

							n1 = n1 + 1;
						}
					} else {
						if (Math.abs(h[0]) > 0.0) { // TOLL
							xStationList.add(round(coordinate.x, 3));
							yStationList.add(round(coordinate.y, 3));
							zStationList.add(z);
							hStationList.add(h[0]);
							idStationList.add(id);

							n1 = n1 + 1;
						}
					}

				}
			}
		} finally {
			stationsIter.close();
		}

		int nStaz = xStationList.size();
		/*
		 * The coordinates of the station points plus in last position a place
		 * for the coordinate of the point to interpolate.
		 */
		double[] xStationOK = new double[nStaz + 1];
		double[] yStationOK = new double[nStaz + 1];
		double[] zStationOK = new double[nStaz + 1];
		double[] hStationOK = new double[nStaz + 1];
		int[] idStationOK = new int[nStaz + 1];

		boolean areAllEquals = true;
		if (nStaz != 0) {
			xStationOK[0] = xStationList.get(0);
			yStationOK[0] = yStationList.get(0);
			zStationOK[0] = zStationList.get(0);
			hStationOK[0] = hStationList.get(0);
			idStationOK[0] = idStationList.get(0);
			double previousValue = hStationOK[0];

			for (int i = 1; i < nStaz; i++) {

				double xTmp = xStationList.get(i);
				double yTmp = yStationList.get(i);
				double zTmp = zStationList.get(i);
				double hTmp = hStationList.get(i);
				int idTmp = idStationList.get(i);

				boolean doubleStation = ModelsEngine.verifyDoubleStation(
						xStationOK, yStationOK, zStationOK, hStationOK, xTmp,
						yTmp, zTmp, hTmp, i, false, pm);
				if (!doubleStation) {
					xStationOK[i] = xTmp;
					yStationOK[i] = yTmp;
					zStationOK[i] = zTmp;
					hStationOK[i] = hTmp;
					idStationOK[i] = idTmp;
					if (areAllEquals && hStationOK[i] != previousValue) {
						areAllEquals = false;
					}
					previousValue = hStationOK[i];
				}
			}
		}

		LinkedHashMap<Integer, Coordinate> pointsToInterpolateId2Coordinates = null;

		int numPointToInterpolate = 0;

		/*
		 * if the isLogarithmic is true then execute the model with log value.
		 */

		if (pMode == 0) {
			pointsToInterpolateId2Coordinates = getCoordinate(
					numPointToInterpolate, inInterpolate, fInterpolateid);
		} 

		Set<Integer> pointsToInterpolateIdSet = pointsToInterpolateId2Coordinates
				.keySet();
		Iterator<Integer> idIterator = pointsToInterpolateIdSet.iterator();

		int j = 0;

		int[] idArray = new int[pointsToInterpolateId2Coordinates.size()];

		double[] result = new double[pointsToInterpolateId2Coordinates.size()];

		while (idIterator.hasNext()) {
			n1 = xStationOK.length - 1;
			double sum = 0.;
			int id = idIterator.next();
			idArray[j] = id;
			Coordinate coordinate = (Coordinate) pointsToInterpolateId2Coordinates
					.get(id);

			// coordinate of the first point 
			xStationOK[n1] = coordinate.x;
			yStationOK[n1] = coordinate.y;
			zStationOK[n1] = coordinate.z;

			double[] xStation = xStationOK;
			double[] yStation = yStationOK;
			double[] zStation = zStationOK;
			double[] hStation = hStationOK;			
			int[] idStation = idStationOK;

			double[] xStationNeww = new double[n1];
			double[] yStationNeww = new double[n1];
			double[] zStationNeww = new double[n1];
			double[] hneww = new double[n1];

			if (inNumCloserStations > 0) {
				if (inNumCloserStations > nStaz) inNumCloserStations = nStaz;

				double[] xStationNew = new double[inNumCloserStations + 1];
				double[] yStationNew = new double[inNumCloserStations + 1];
				int[] idStationNew = new int[inNumCloserStations + 1];

				double[] hnew = new double[inNumCloserStations];

				xStationNeww = new double[inNumCloserStations + 1];
				yStationNeww = new double[inNumCloserStations + 1];
				zStationNeww = new double[inNumCloserStations + 1];

				int[] idStationNeww = new int[inNumCloserStations + 1];

				hneww = new double[inNumCloserStations];

				double[] zStationNew = new double[inNumCloserStations + 1];

				double x2, y2;
				double dDifX, dDifY;
				int iCount = xStationOK.length;
				double distanceVector[] = new double[iCount];
				double pos[] = new double[iCount];

				for (int jj = 0; jj < iCount; jj++) {

					x2 = xStationOK[jj];
					y2 = yStationOK[jj];

					dDifX = xStationOK[n1] - x2;
					dDifY = yStationOK[n1] - y2;
					distanceVector[jj] = Math.sqrt(dDifX * dDifX + dDifY * dDifY); 
					pos[jj] = jj;					
				}

				QuickSortAlgorithm t = new QuickSortAlgorithm(pm);
				t.sort(distanceVector, pos);
				n1 = 0;
				for (int i = 1; i < distanceVector.length; i++) {
					if (n1 < inNumCloserStations) {
						if (defaultVariogramMode == 0) {
							if (doIncludezero) {
								if (Math.abs(hStationOK[(int) pos[i]]) >= 0.0) { // TOLL
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;
								}
							} else {
								if (Math.abs(hStationOK[(int) pos[i]]) > 0.0) {
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;

								}
							}

						} else if (defaultVariogramMode == 1) {
							if (doIncludezero) {
								if (Math.abs(hStationOK[(int) pos[i]]) >= 0.0) { // TOLL
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;
								}
							} else {
								if (Math.abs(hStationOK[(int) pos[i]]) > 0.0) {
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;

								}
							}
						}

					} else {
						break;
					}

				}
				if (n1 > 1) {
					// System.out.println(n1);
					areAllEquals = true;
					xStationNeww[0] = xStationNew[0];
					yStationNeww[0] = yStationNew[0];
					zStationNeww[0] = zStationNew[0];
					idStationNeww[0] = idStationNew[0];

					hneww[0] = hnew[0];
					double previousValue = hnew[0];

					for (int i = 1; i < xStationNew.length - 1; i++) {

						double xTmp = xStationNew[i];
						double yTmp = yStationNew[i];
						double zTmp = zStationNew[i];
						int idTmp = idStationNew[i];
						// System.out.println(idStationNew[i]);

						double hTmp = hnew[i];
						boolean doubleStation = ModelsEngine
								.verifyDoubleStation(xStationNeww,
										yStationNeww, zStationNeww, hneww,
										xTmp, yTmp, zTmp, hTmp, i, false, pm);
						if (!doubleStation) {
							xStationNeww[i] = xTmp;
							yStationNeww[i] = yTmp;
							zStationNeww[i] = zTmp;
							idStationNeww[i] = idTmp;

							hneww[i] = hTmp;
							if (areAllEquals && hneww[i] != previousValue) {
								areAllEquals = false;
							}
							previousValue = hneww[i];

						}

					}

					yStationNew[inNumCloserStations] = coordinate.y;
					xStationNew[inNumCloserStations] = coordinate.x;
					zStationNew[inNumCloserStations] = coordinate.z;
					idStationNew[inNumCloserStations] = id;
					xStation = xStationNeww;
					yStation = yStationNeww;
					zStation = zStationNeww;
					idStation = idStationNeww;
					hStation = hneww;
					for (int i = 0; i < xStation.length; i++) {
						System.out.println(idStationNew[i]);
					}

				} else {

					xStation = xStationNew;
					yStation = yStationNew;
					zStation = zStationNew;
					idStation = idStationNew;
					hStation = hnew;
				}
			} else {
				if (maxdist > 0) {

					double x2, y2;
					double dDifX, dDifY;
					int iCount = xStationOK.length;
					double d[] = new double[iCount];
					double pos[] = new double[iCount];

					for (int jj = 0; jj < iCount; jj++) {

						x2 = xStationOK[jj];
						y2 = yStationOK[jj];

						dDifX = xStationOK[n1] - x2;
						dDifY = yStationOK[n1] - y2;
						d[jj] = Math.sqrt(dDifX * dDifX + dDifY * dDifY); 
						pos[jj] = jj;

					}

					QuickSortAlgorithm t = new QuickSortAlgorithm(pm);
					t.sort(d, pos);
					n1 = 0;
					int posDist = d.length;
					for (int k = 0; k < d.length; k++) {
						if (d[k] > maxdist) {
							posDist = k;
							break;
						}
					}
					if (posDist == 1) {
						posDist += 3;
					}
					double[] xStationNew = new double[posDist];
					double[] yStationNew = new double[posDist];
					int[] idStationNew = new int[posDist];

					double[] hnew = new double[posDist - 1];

					xStationNeww = new double[posDist];
					yStationNeww = new double[posDist];
					zStationNeww = new double[posDist];

					int[] idStationNeww = new int[posDist];

					hneww = new double[posDist - 1];

					double[] zStationNew = new double[posDist];

					for (int i = 1; i < posDist; i++) {
						if (defaultVariogramMode == 0) {
							if (doIncludezero) {
								if (Math.abs(hStationOK[(int) pos[i]]) >= 0.0) { // TOLL
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;
								}
							} else {
								if (Math.abs(hStationOK[(int) pos[i]]) > 0.0) {
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;

								}
							}

						} else if (defaultVariogramMode == 1) {
							if (doIncludezero) {
								if (Math.abs(hStationOK[(int) pos[i]]) >= 0.0) { // TOLL
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;
								}
							} else {
								if (Math.abs(hStationOK[(int) pos[i]]) > 0.0) {
									xStationNew[n1] = xStationOK[(int) pos[i]];
									yStationNew[n1] = yStationOK[(int) pos[i]];
									zStationNew[n1] = zStationOK[(int) pos[i]];
									idStationNew[n1] = idStationOK[(int) pos[i]];

									hnew[n1] = hStationOK[(int) pos[i]];
									n1 += 1;

								}
							}
						}

					}
					if (n1 > 1) {
						// System.out.println(n1);
						areAllEquals = true;
						xStationNeww[0] = xStationNew[0];
						yStationNeww[0] = yStationNew[0];
						zStationNeww[0] = zStationNew[0];
						idStationNeww[0] = idStationNew[0];

						hneww[0] = hnew[0];
						double previousValue = hnew[0];

						for (int i = 1; i < xStationNew.length - 1; i++) {

							double xTmp = xStationNew[i];
							double yTmp = yStationNew[i];
							double zTmp = zStationNew[i];
							int idTmp = idStationNew[i];
							// System.out.println(idStationNew[i]);

							double hTmp = hnew[i];
							boolean doubleStation = ModelsEngine
									.verifyDoubleStation(xStationNeww,
											yStationNeww, zStationNeww, hneww,
											xTmp, yTmp, zTmp, hTmp, i, false,
											pm);
							if (!doubleStation) {
								xStationNeww[i] = xTmp;
								yStationNeww[i] = yTmp;
								zStationNeww[i] = zTmp;
								idStationNeww[i] = idTmp;

								hneww[i] = hTmp;
								if (areAllEquals && hneww[i] != previousValue) {
									areAllEquals = false;
								}
								previousValue = hneww[i];

							}

						}

						yStationNeww[posDist - 1] = coordinate.y;
						xStationNeww[posDist - 1] = coordinate.x;
						zStationNeww[posDist - 1] = coordinate.z;
						idStationNeww[posDist - 1] = id;
						xStation = xStationNeww;
						yStation = yStationNeww;
						zStation = zStationNeww;
						idStation = idStationNeww;
						hStation = hneww;
						for (int i = 0; i < xStation.length; i++) {
							System.out.println(idStationNeww[i]);
						}

					} else {

						xStation = xStationNew;
						yStation = yStationNew;
						zStation = zStationNew;
						idStation = idStationNew;
						hStation = hnew;
					}

				}
			}

			if (n1 != 0) {
				if (doLogarithmic) {
					for (int i = 0; i < nStaz; i++) {
						if (hStation[i] > 0.0) {
							hStation[i] = Math.log(hStation[i]);
						}
					}

				}

				if (constrainGT0) {
					for (int i = 0; i < n1; i++) {
						if (hStation[i] < 0.0) {
							hStation[i] = 0;
						}
					}
				}
				// for (int yyy = 0; yyy < hStation.length; yyy++) {
				// System.out.print(hStation[yyy] + " ");
				//
				// }
				// System.out.println(areAllEquals);
				/*
				 * calculating the covariance matrix.
				 */

				/*
				 * extract the coordinate of the points where interpolated.
				 */

				/*
				 * initialize the solution and its variance vector.
				 */

				if (!areAllEquals && n1 > 1) {
					// pm.beginTask(msg.message("kriging.working"),inInterpolate.size());
					pm.beginTask(msg.message("kriging.working"),
							pointsToInterpolateId2Coordinates.size());
					idArray[j] = id;

					xStation[n1] = coordinate.x;
					yStation[n1] = coordinate.y;
					zStation[n1] = coordinate.z;
					/*
					 * calculating the right hand side of the kriging linear
					 * system.
					 */

					double h0 = 0.0;

					outDistances = inDistances;
					outSemivarinces = inSemivarinces;


					double[][] covarianceMatrix = covMatrixCalculating(xStation, yStation, zStation, n1);
					
					double[] knownTerm = knownTermsCalculation(xStation,yStation, zStation, n1);

					/*
					 * solve the linear system, where the result is the weight.
					 */
					ColumnVector knownTermColumn = new ColumnVector(knownTerm);
					
					LinearSystem linearSystem = new LinearSystem(covarianceMatrix);
					
					ColumnVector solution = linearSystem.solve(knownTermColumn,true);
					// Matrix a = new Matrix(covarianceMatrix);
					// Matrix b = new Matrix(knownTerm, knownTerm.length);
					// Matrix x = a.solve(b);

					double[] moltiplicativeFactor = solution.copyValues1D();

					h0 = 0.0;
					for (int k = 0; k < n1; k++) {
						h0 = h0 + moltiplicativeFactor[k] * hStation[k];
						sum = sum + moltiplicativeFactor[k];

					}

					if (doLogarithmic) {
						h0 = Math.exp(h0);
					}
					result[j] = h0;
					j++;

					if (Math.abs(sum - 1) >= TOLL) {
						throw new ModelsRuntimeException(
								"Error in the coffeicients calculation", this
								.getClass().getSimpleName());
					}
					pm.worked(1);
				} else if (n1 == 1 || areAllEquals) {
					outDistances = inDistances;
					outSemivarinces = inSemivarinces;
					double tmp = hStation[0];
					pm.message(msg.message("kriging.setequalsvalue"));
					pm.beginTask(msg.message("kriging.working"),
							pointsToInterpolateId2Coordinates.size());
					result[j] = tmp;
					j++;
					n1 = 0;
					pm.worked(1);

				}

				pm.done();

			} else {
				outDistances = inDistances;
				outSemivarinces = inSemivarinces;
				pm.errorMessage("No rain for this time step");
				j = 0;
				double[] value = inData.values().iterator().next();
				result[j] = value[0];
				j++;

			}
		}

		if (pMode == 0) {
			storeResult(result, idArray);
		} 
	}

	/**
	 * Verify the input of the model.
	 */
	private void verifyInput() {
		if (inData == null || inStations == null) {
			throw new NullPointerException( msg.message("kriging.stationProblem"));
		}
		if (pMode < 0 || pMode > 1) {
			throw new IllegalArgumentException( msg.message("kriging.defaultMode"));
		}
		// if (pMode == 0 && (fStationsZ == null || fPointZ == null)) {
		// pm.errorMessage(msg.message("kriging.noElevation"));
		// throw new
		// IllegalArgumentException(msg.message("kriging.noElevation"));
		// }

		if (defaultVariogramMode != 0 && defaultVariogramMode != 1) {
			throw new IllegalArgumentException( msg.message("kriging.variogramMode"));
		}
		if (defaultVariogramMode == 0) {
			if (pVariance == 0 || pIntegralscale[0] == 0
					|| pIntegralscale[1] == 0 || pIntegralscale[2] == 0) {

				pm.errorMessage(msg.message("kriging.noParam"));
				pm.errorMessage("varianza " + pVariance);
				pm.errorMessage("Integral scale x " + pIntegralscale[0]);
				pm.errorMessage("Integral scale y " + pIntegralscale[1]);
				pm.errorMessage("Integral scale z " + pIntegralscale[2]);
			}
		}
		if (defaultVariogramMode == 1) {
			if (pNug == 0 || pS == 0 || pA == 0) {
				pm.errorMessage(msg.message("kriging.noParam"));
				pm.errorMessage("Nugget " + pNug);
				pm.errorMessage("Sill " + pS);
				pm.errorMessage("Range " + pA);
			}
		}

		if ((pMode == 0) && inInterpolate == null) {
			throw new ModelsIllegalargumentException(
					msg.message("kriging.noPoint"), this);
		}
		if (pMode == 1 && inInterpolationGrid == null) {
			throw new ModelsIllegalargumentException(
					"The gridded interpolation needs a gridgeometry in input.",
					this);
		}

	}
	
	
	public static double round(double value, int places) {
		if (places < 0)
			throw new IllegalArgumentException();

		long factor = (long) Math.pow(10, places);
		value = value * factor;
		long tmp = Math.round(value);
		return (double) tmp / factor;
	}




	/**
	 * Extract the coordinate of a FeatureCollection in a HashMap with an ID as
	 * a key.
	 * 
	 * @param nStaz
	 * @param collection
	 * @throws Exception
	 *             if a field of elevation isn't the same of the collection
	 */
	private LinkedHashMap<Integer, Coordinate> getCoordinate(int nStaz,
			SimpleFeatureCollection collection, String idField)
					throws Exception {
		LinkedHashMap<Integer, Coordinate> id2CoordinatesMcovarianceMatrix = new LinkedHashMap<Integer, Coordinate>();
		FeatureIterator<SimpleFeature> iterator = collection.features();
		Coordinate coordinate = null;
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				int name = ((Number) feature.getAttribute(idField)).intValue();
				coordinate = ((Geometry) feature.getDefaultGeometry())
						.getCentroid().getCoordinate();
				double z = 0;
				if (fPointZ != null) {
					try {
						z = ((Number) feature.getAttribute(fPointZ))
								.doubleValue();
					} catch (NullPointerException e) {
						pm.errorMessage(msg.message("kriging.noPointZ"));
						throw new Exception(msg.message("kriging.noPointZ"));
					}
				}
				coordinate.z = z;
				id2CoordinatesMcovarianceMatrix.put(name, coordinate);
			}
		} finally {
			iterator.close();
		}

		return id2CoordinatesMcovarianceMatrix;
	}
	
	


	/**
	 * 
	 * 
	 * @param x
	 *            the x coordinates.
	 * @param y
	 *            the y coordinates.
	 * @param z
	 *            the z coordinates.
	 * @param n
	 *            the number of the stations points.
	 * @return
	 */
	private double[][] covMatrixCalculating(double[] x, double[] y, double[] z, int n) {
		
		double[][] covarianceMatrix = new double[n + 1][n + 1];
		
		if (defaultVariogramMode == 0) {
			for (int j = 0; j < n; j++) {
				for (int i = 0; i <= j; i++) {
					double rx = x[i] - x[j];
					double ry = y[i] - y[j];
					double rz = 0;
					if (pMode == 0) {
						rz = z[i] - z[j];
					}

					covarianceMatrix[j][i] = variogram(rx, ry, rz);
					covarianceMatrix[i][j] = variogram(rx, ry, rz);

				}
			}
		} else if (defaultVariogramMode == 1) {
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					double rx = x[i] - x[j];
					double ry = y[i] - y[j];
					double rz = 0;
					if (pMode == 0) {
						rz = z[i] - z[j];
					}

					covarianceMatrix[j][i] = variogram(pNug, pA, pS, rx, ry, rz);
					covarianceMatrix[i][j] = variogram(pNug, pA, pS, rx, ry, rz);

				}
			}

		}
		for (int i = 0; i < n; i++) {
			covarianceMatrix[i][n] = 1.0;
			covarianceMatrix[n][i] = 1.0;

		}
		covarianceMatrix[n][n] = 0;
		return covarianceMatrix;

	}

	/**
	 * 
	 * @param x
	 *            the x coordinates.
	 * @param y
	 *            the y coordinates.
	 * @param z
	 *            the z coordinates.
	 * @param n
	 *            the number of the stations points.
	 * @return
	 */
	private double[] knownTermsCalculation(double[] x, double[] y, double[] z,
			int n) {

		// known terms vector 
		double[] gamma = new double[n + 1];
		
		if (defaultVariogramMode == 0) {
			for (int i = 0; i < n; i++) {
				double rx = x[i] - x[n];
				double ry = y[i] - y[n];
				double rz = z[i] - z[n];
				gamma[i] = variogram(rx, ry, rz);
			}
		} else if (defaultVariogramMode == 1) {
			for (int i = 0; i < n; i++) {
				double rx = x[i] - x[n];
				double ry = y[i] - y[n];
				double rz = z[i] - z[n];
				gamma[i] = variogram(pNug, pA, pS, rx, ry, rz);
			}

		}
		gamma[n] = 1.0;
		return gamma;

	}



	private double variogram(double nug, double range, double sill, double rx, double ry, double rz) {
		if (isNovalue(rz)) {
			rz = 0;
		}
		double h2 = Math.sqrt(rx * rx + rz * rz + ry * ry);			
		modelVGM=SimpleModelFactory.createModel(pSemivariogramType,h2, sill, range, nug);
		double value=modelVGM.result();

		return value;
	}
	
	/**
	 * 
	 * @param rx
	 *            x distance.
	 * @param ry
	 *            y distance.
	 * @param rz
	 *            z distance.
	 * @return
	 */
	private double variogram(double rx, double ry, double rz) {
		if (isNovalue(rz)) {
			rz = 0;
		}
		double h2 = (rx / pIntegralscale[0]) * (rx / pIntegralscale[0])
				+ (ry / pIntegralscale[1]) * (ry / pIntegralscale[1])
				+ (rz / pIntegralscale[2]) * (rz / pIntegralscale[2]);
		if (h2 < TOLL) {
			return pVariance;
		} else {
			return pVariance * Math.exp(-Math.sqrt(h2));
		}

	}

	/**
	 * Store the result in a HashMcovarianceMatrix (if the mode is 0 or 1)
	 * 
	 * @param result2
	 *            the result of the model
	 * @param id
	 *            the associated id of the calculating points.
	 * @throws SchemaException
	 * @throws SchemaException
	 */
	private void storeResult(double[] result2, int[] id) throws SchemaException {
		outData = new HashMap<Integer, double[]>();
		for (int i = 0; i < result2.length; i++) {
			outData.put(id[i], new double[] { result2[i] });
		}
	}

}
