package application;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

public class ImageHandler {

	public BufferedImage NormalizedConvolution(BufferedImage localImage, double sigmaSpace, double sigmaRange) {
		int imageHeight = localImage.getHeight();
		int imageWidth = localImage.getWidth();
		int channels = 3;

		double dIcdy[][][] = new double[imageHeight - 1][imageWidth][4];
		double dIcdx[][][] = new double[imageHeight][imageWidth - 1][4];

		double diffX[][] = new double[imageHeight][imageWidth];
		double diffY[][] = new double[imageHeight][imageWidth];

		double dHdx[][] = new double[imageHeight][imageWidth];
		double dVdy[][] = new double[imageHeight][imageWidth];

		double ct_H[][] = new double[imageHeight][imageWidth];
		double ct_V[][] = new double[imageHeight][imageWidth];

		Color rgbRow = null;
		Color rgbCol = null;

		double sumH = 0;
		double sumV = 0;

		int nIterations = 3;

		/* DOMAIN TRANSFORM COMPUTATION */

		for (int col = 0; col < imageWidth; col++) {
			for (int row = 0; row < imageHeight; row++) {

				Color rgb = new Color(localImage.getRGB(col, row));
				if (row < imageHeight - 1) {
					rgbRow = new Color(localImage.getRGB(col, row + 1));
					double redDiffRow = rgbRow.getRed() - rgb.getRed();
					double greenDiffRow = rgbRow.getGreen() - rgb.getGreen();
					double blueDiffRow = rgbRow.getBlue() - rgb.getBlue();
					dIcdy[row][col][1] = redDiffRow / 255;
					dIcdy[row][col][2] = greenDiffRow / 255;
					dIcdy[row][col][3] = blueDiffRow / 255;
				}
				if (col < imageWidth - 1) {
					rgbCol = new Color(localImage.getRGB(col + 1, row));
					double redDiffCol = rgbCol.getRed() - rgb.getRed();
					double greenDiffCol = rgbCol.getGreen() - rgb.getGreen();
					double blueDiffCol = rgbCol.getBlue() - rgb.getBlue();
					dIcdx[row][col][1] = redDiffCol / 255;
					dIcdx[row][col][2] = greenDiffCol / 255;
					dIcdx[row][col][3] = blueDiffCol / 255;
				}

				if (row < imageHeight - 1)
					diffY[row + 1][col] = diffY[row + 1][col] + Math.abs(dIcdy[row][col][1])
							+ Math.abs(dIcdy[row][col][2]) + Math.abs(dIcdy[row][col][3]);

				if (col < imageWidth - 1)
					diffX[row][col + 1] = diffX[row][col + 1] + Math.abs(dIcdx[row][col][1])
							+ Math.abs(dIcdx[row][col][2]) + Math.abs(dIcdx[row][col][3]);

				// Derivatives of the horizontal and vertical domain transforms
				dHdx[row][col] = (1 + sigmaSpace / sigmaRange * diffX[row][col]);
				dVdy[row][col] = (1 + sigmaSpace / sigmaRange * diffY[row][col]);
			}
		}

		// Integration of the domain transforms
		for (int row = 0; row < imageHeight; row++) {
			for (int col = 0; col < imageWidth; col++) {
				sumH = sumH + dHdx[row][col];
				ct_H[row][col] = sumH;
			}
			sumH = 0;
		}

		for (int col = 0; col < imageWidth; col++) {
			for (int row = 0; row < imageHeight; row++) {
				sumV = sumV + dVdy[row][col];
				ct_V[row][col] = sumV;
			}
			sumV = 0;
		}

		double transposeCt_V[][] = new double[imageWidth][imageHeight];

		for (int row = 0; row < imageWidth; row++) {
			for (int col = 0; col < imageHeight; col++) {
				transposeCt_V[row][col] = ct_V[col][row];
			}
		}

		double F[][][] = new double[imageHeight][imageWidth][channels + 1];

		/* FILTERING */
		double sigma_H = sigmaSpace;
		for (int i = 0; i < nIterations - 1; i++) {
			// Sigma value for iteration i
			double sigma_H_i = sigma_H * Math.sqrt(3) * Math.pow(2, nIterations - (i + 1))
					/ Math.sqrt(Math.pow(4, nIterations) - 1);

			// Radius of the box filter with desired variance
			double boxRadius = Math.sqrt(3) * sigma_H_i;

			for (int row = 0; row < imageHeight; row++) {
				for (int col = 0; col < imageWidth; col++) {
					Color rgb = new Color(localImage.getRGB(col, row));
					F[row][col][1] = (double) (rgb.getRed()) / 255;
					F[row][col][2] = (double) (rgb.getGreen()) / 255;
					F[row][col][3] = (double) (rgb.getBlue()) / 255;
				}
			}

			F = TransformedDomainBoxFilterConvolution(F, ct_H, boxRadius);
			double transposeF[][][] = new double[imageWidth][imageHeight][channels + 1];

			for (int row = 0; row < imageHeight; row++) {
				for (int col = 0; col < imageWidth; col++) {
					for (int c = 1; c < channels + 1; c++) {
						transposeF[col][row][c] = F[row][col][c];
					}
				}
			}

			double auxF[][][] = new double[imageWidth][imageHeight][channels + 1];

			auxF = TransformedDomainBoxFilterConvolution(transposeF, transposeCt_V, boxRadius);

			double newTransposeF[][][] = new double[imageHeight][imageWidth][channels + 1];

			for (int row = 0; row < imageHeight; row++) {
				for (int col = 0; col < imageWidth; col++) {
					for (int c = 1; c < channels + 1; c++) {
						newTransposeF[row][col][c] = auxF[col][row][c];
					}
				}
			}

			for (int row = 0; row < imageHeight; row++) {
				for (int col = 0; col < imageWidth; col++) {
					for (int c = 1; c < channels + 1; c++) {
						F[row][col][c] = newTransposeF[row][col][c];
					}
				}
			}
		}

		BufferedImage filteredImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);

		for (int row = 0; row < imageHeight; row++) {
			for (int col = 0; col < imageWidth; col++) {
				Color rgb = new Color((int) (F[row][col][1] * 255), (int) (F[row][col][2] * 255),
						(int) (F[row][col][3] * 255));
				filteredImage.setRGB(col, row, rgb.getRGB());
			}
		}

		return filteredImage;
	}

	public double[][][] TransformedDomainBoxFilterConvolution(double F[][][], double domainPosition[][],
			double boxRadius) {
		int imageHeight = F.length;
		int imageWidth = F[0].length;
		int channels = 3;

		// Determines the lower and upper limits of the box kernel in the transformed
		// domain
		double lowerPosition[][] = new double[domainPosition.length][domainPosition[0].length];
		double upperPosition[][] = new double[domainPosition.length][domainPosition[0].length];

		for (int row = 0; row < imageHeight; row++) {
			for (int col = 0; col < imageWidth; col++) {
				lowerPosition[row][col] = domainPosition[row][col] - boxRadius;
				upperPosition[row][col] = domainPosition[row][col] + boxRadius;
			}
		}

		// Finds the indices of the pixels associated with the lower and upper limits of
		// the box kernel
		int l_idx[][] = new int[domainPosition.length][domainPosition[0].length];
		int u_idx[][] = new int[domainPosition.length][domainPosition[0].length];

		double domainPositionRow[] = new double[imageWidth + 1];
		double l_pos_row[] = new double[domainPosition[0].length];
		double u_pos_row[] = new double[domainPosition[0].length];
		int local_l_idx[] = new int[imageWidth];
		int local_u_idx[] = new int[imageWidth];

		int find = 0;

		for (int row = 0; row < imageHeight; row++) {

			for (int c = 0; c < imageWidth; c++) {
				domainPositionRow[c] = domainPosition[row][c];
				l_pos_row[c] = lowerPosition[row][c];
				u_pos_row[c] = upperPosition[row][c];
			}

			domainPositionRow[imageWidth] = Double.POSITIVE_INFINITY;

			for (int i = 0; i < imageWidth; i++) {
				local_l_idx[i] = 0;
				local_u_idx[i] = 0;
			}

			for (int i = 0; i < imageWidth; i++) {
				if (domainPositionRow[i] > l_pos_row[0]) {
					local_l_idx[0] = i + 1;
					break;
				}
			}

			for (int i = 0; i < imageWidth; i++) {
				if (domainPositionRow[i] > u_pos_row[0]) {
					local_u_idx[0] = i + 1;
					break;
				}
			}

			for (int col = 1; col < imageWidth; col++) {
				find = 0;
				int counter = 0;

				for (int i = local_l_idx[col - 1]; i < imageWidth; i++) {
					counter++;
					if (domainPositionRow[i - 1] > l_pos_row[col]) {
						find = counter;
						break;
					}
				}

				local_l_idx[col] = local_l_idx[col - 1] + find - 1;

				find = 0;
				counter = 0;
				for (int i = local_u_idx[col - 1]; i < imageWidth; i++) {
					counter++;
					if (domainPositionRow[i - 1] > u_pos_row[col]) {
						find = counter;
						break;
					}
				}

				local_u_idx[col] = local_u_idx[col - 1] + find - 1;
			}

			for (int i = 0; i < imageWidth; i++) {
				l_idx[row][i] = local_l_idx[i];
				u_idx[row][i] = local_u_idx[i];
			}

		}

		// Box filter computation using summed area table
		double SAT[][][] = new double[imageHeight][imageWidth + 1][channels + 1];
		double sumR = 0.0;
		double sumG = 0.0;
		double sumB = 0.0;

		for (int row = 0; row < imageHeight; row++) {
			for (int col = 0; col < imageWidth; col++) {

				if (col == 0) {
					SAT[row][col][1] = 0.0;
					SAT[row][col][2] = 0.0;
					SAT[row][col][3] = 0.0;
				} else {
					sumR += F[row][col][1];
					sumG += F[row][col][2];
					sumB += F[row][col][3];
					SAT[row][col][1] = sumR;
					SAT[row][col][2] = sumG;
					SAT[row][col][3] = sumB;
				}
			}
			sumR = 0.0;
			sumG = 0.0;
			sumB = 0.0;
		}

		int row_indices[][] = new int[imageHeight][imageWidth];

		for (int i = 0; i < imageHeight; i++) {
			for (int j = 0; j < imageWidth; j++) {
				row_indices[i][j] = i + 1;
			}
		}

		int a[][] = new int[imageHeight][imageWidth];
		int b[][] = new int[imageHeight][imageWidth];
		int rowA, colA, channelsA, rowB, colB, channelsB, indexA, indexB;

		for (int row = 0; row < imageHeight; row++) {
			for (int col = 0; col < imageWidth; col++) {
				for (int c = 1; c < 4; c++) {
					a[row][col] = (imageHeight * imageWidth) * (c - 1) + (l_idx[row][col] - 1) * imageHeight
							+ row_indices[row][col];
					b[row][col] = (imageHeight * imageWidth) * (c - 1) + (u_idx[row][col] - 1) * imageHeight
							+ row_indices[row][col];

					indexA = a[row][col];
					channelsA = indexA / (imageHeight * imageWidth) + 1;
					rowA = indexA - (channelsA - 1) * (imageHeight * imageWidth);

					if (rowA > imageHeight)
						rowA = rowA % imageHeight;

					colA = (indexA - (channelsA - 1) * (imageHeight * imageWidth)) / imageHeight + 1;

					indexB = b[row][col];
					channelsB = indexB / (imageHeight * imageWidth) + 1;
					rowB = indexB - (channelsB - 1) * (imageHeight * imageWidth);

					if (rowB > imageHeight)
						rowB = rowB % imageHeight;

					colB = (indexB - (channelsB - 1) * (imageHeight * imageWidth)) / imageHeight + 1;

					if (rowB == 0)
						rowB = imageHeight;
					if (rowA == 0)
						rowA = imageHeight;

					F[row][col][c] = (SAT[rowA - 1][colA - 1][channelsA] - SAT[rowB - 1][colB - 1][channelsB])
							/ (u_idx[row][col] - l_idx[row][col]);
				}
			}
		}
		return F;

	}

	static Image matToImage(Mat frame) {
		try {
			return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
		} catch (Exception e) {
			System.err.println("Cannot convert the Mat object: " + e);
			return null;
		}
	}

	static BufferedImage matToBufferedImage(Mat matrix) throws IOException {
		BufferedImage bufferedImage = null;
		int width = matrix.width(), height = matrix.height(), channels = matrix.channels();
		byte[] sourcePixels = new byte[width * height * channels];
		matrix.get(0, 0, sourcePixels);

		if (matrix.channels() > 1) {
			bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		} else {
			bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		}
		final byte[] targetPixels = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();
		System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);

		return bufferedImage;
	}

	public Mat bufferedImageToMat(BufferedImage bufferedImage) {
        Mat matrix;
        byte[] data;
        int red, green, blue;
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int type = bufferedImage.getType();
        int[] dataBuff = bufferedImage.getRGB(0, 0, width, height, null, 0, width);

        if (type == BufferedImage.TYPE_INT_RGB) {
            matrix = new Mat(height, width, CvType.CV_8UC3);
            data = new byte[width * height * (int) matrix.elemSize()];            
            for (int i = 0; i < dataBuff.length; i++) {
                data[i * 3] = (byte) ((dataBuff[i] >> 0) & 0xFF);
                data[i * 3 + 1] = (byte) ((dataBuff[i] >> 8) & 0xFF);
                data[i * 3 + 2] = (byte) ((dataBuff[i] >> 16) & 0xFF);
            }
        } else {
            matrix = new Mat(height, width, CvType.CV_8UC1);
            data = new byte[width * height * (int) matrix.elemSize()];            
            for (int i = 0; i < dataBuff.length; i++) {
                red = (byte) ((dataBuff[i] >> 0) & 0xFF);
                green = (byte) ((dataBuff[i] >> 8) & 0xFF);
                blue = (byte) ((dataBuff[i] >> 16) & 0xFF);
                data[i] = (byte) ((0.21 * red) + (0.71 * green) + (0.07 * blue));
            }
        }
        matrix.put(0, 0, data);
        return matrix;
	}
}
