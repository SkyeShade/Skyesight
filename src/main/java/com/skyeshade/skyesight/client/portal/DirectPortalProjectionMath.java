package com.skyeshade.skyesight.client.portal;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Direct-stencil portal projection diagnostics.
 *
 * <p>Direct rendering maps the secondary full-screen NDC square into the entrance portal's
 * NDC quad as a clip-space homography.</p>
 */
public final class DirectPortalProjectionMath {
    private static final float MIN_NDC_AREA = 1.0E-4F;
    private static final float MAX_CORNER_ERROR = 0.05F;

    private DirectPortalProjectionMath() {}

    public static Result legacyProjectiveEquivalent(
            Matrix4f legacyPortalProjection,
            Matrix4f mainViewProjection,
            PortalFrame entrancePortal,
            Vec3 secondaryCameraPosition,
            PortalFrame exitPortal
    ) {
        ProjectedQuad entrance = projectWorldPortal(mainViewProjection, entrancePortal);

        if (!entrance.valid()) {
            return Result.invalid(
                    "entrance projection invalid",
                    entrance,
                    ProjectedQuad.invalid()
            );
        }

        double[] squareToEntranceHomography = homography(
                new double[][] {
                        {-1.0D, -1.0D},
                        {1.0D, -1.0D},
                        {1.0D, 1.0D},
                        {-1.0D, 1.0D}
                },
                entrance
        );
        double[] entranceToSquareHomography = invertHomography(squareToEntranceHomography);
        Candidate best = bestCandidate(
                legacyPortalProjection,
                secondaryCameraPosition,
                exitPortal,
                entrance,
                squareToEntranceHomography,
                entranceToSquareHomography
        );

        return new Result(
                best.valid(),
                best.reason(),
                best.correctionMatrix(),
                best.correctedProjection(),
                entrance,
                best.correctedExitNdc(),
                best.cornerError(),
                best.baseExitNdc(),
                best.direction(),
                best.multiplicationOrder(),
                best.determinant(),
                best.inverseDeterminant(),
                best.correctedArea()
        );
    }

    private static Candidate bestCandidate(
            Matrix4f baseProjection,
            Vec3 secondaryCameraPosition,
            PortalFrame exitPortal,
            ProjectedQuad entrance,
            double[] squareToEntranceHomography,
            double[] entranceToSquareHomography
    ) {
        ProjectedQuad baseExitNdc = projectExitPortal(
                baseProjection,
                secondaryCameraPosition,
                PortalFrameMath.portalRenderRotation(exitPortal),
                exitPortal
        );
        Candidate best = Candidate.invalid("no candidate", baseProjection, baseExitNdc);
        double squareToEntranceDet = determinant(squareToEntranceHomography);
        double entranceToSquareDet = determinant(entranceToSquareHomography);
        Candidate[] candidates = new Candidate[] {
                evaluateCandidate(
                        "FULL_TO_ENTRANCE",
                        "H * P",
                        squareToEntranceHomography,
                        squareToEntranceDet,
                        entranceToSquareDet,
                        baseProjection,
                        secondaryCameraPosition,
                        exitPortal,
                        entrance,
                        baseExitNdc,
                        true
                ),
                evaluateCandidate(
                        "FULL_TO_ENTRANCE",
                        "P * H",
                        squareToEntranceHomography,
                        squareToEntranceDet,
                        entranceToSquareDet,
                        baseProjection,
                        secondaryCameraPosition,
                        exitPortal,
                        entrance,
                        baseExitNdc,
                        false
                ),
                evaluateCandidate(
                        "ENTRANCE_TO_FULL",
                        "Hinv * P",
                        entranceToSquareHomography,
                        entranceToSquareDet,
                        squareToEntranceDet,
                        baseProjection,
                        secondaryCameraPosition,
                        exitPortal,
                        entrance,
                        baseExitNdc,
                        true
                ),
                evaluateCandidate(
                        "ENTRANCE_TO_FULL",
                        "P * Hinv",
                        entranceToSquareHomography,
                        entranceToSquareDet,
                        squareToEntranceDet,
                        baseProjection,
                        secondaryCameraPosition,
                        exitPortal,
                        entrance,
                        baseExitNdc,
                        false
                )
        };

        for (Candidate candidate : candidates) {
            if (candidate.valid() && (!best.valid() || candidate.cornerError() < best.cornerError())) {
                best = candidate;
            }
        }

        if (best.valid()) {
            return best;
        }

        Candidate lowestError = candidates[0];
        for (Candidate candidate : candidates) {
            if (candidate.cornerError() < lowestError.cornerError()) {
                lowestError = candidate;
            }
        }
        return lowestError.withInvalidReason("no valid correction; best failed " + lowestError.reason());
    }

    private static Candidate evaluateCandidate(
            String direction,
            String multiplicationOrder,
            double[] homography,
            double determinant,
            double inverseDeterminant,
            Matrix4f baseProjection,
            Vec3 secondaryCameraPosition,
            PortalFrame exitPortal,
            ProjectedQuad entrance,
            ProjectedQuad baseExitNdc,
            boolean preMultiply
    ) {
        Matrix4f correction = homographyToClipMatrix(homography);
        Matrix4f correctedProjection = preMultiply
                ? new Matrix4f(correction).mul(baseProjection)
                : new Matrix4f(baseProjection).mul(correction);
        ProjectedQuad correctedExitNdc = projectExitPortal(
                correctedProjection,
                secondaryCameraPosition,
                PortalFrameMath.portalRenderRotation(exitPortal),
                exitPortal
        );
        float area = correctedExitNdc.ndcArea();
        float error = correctedExitNdc.valid()
                ? entrance.maxXyError(correctedExitNdc)
                : Float.POSITIVE_INFINITY;
        String reason = "ok";
        boolean valid = correctedExitNdc.valid()
                && Float.isFinite(area)
                && area > MIN_NDC_AREA
                && Float.isFinite(error)
                && error <= MAX_CORNER_ERROR;

        if (!correctedExitNdc.valid()) {
            reason = "non-finite or invalid corrected corners";
        } else if (!Float.isFinite(area) || area <= MIN_NDC_AREA) {
            reason = "collapsed area " + String.format(Locale.ROOT, "%.6f", area);
        } else if (!Float.isFinite(error) || error > MAX_CORNER_ERROR) {
            reason = "corner error " + String.format(Locale.ROOT, "%.4f", error);
        }

        return new Candidate(
                valid,
                reason,
                direction,
                multiplicationOrder,
                correction,
                correctedProjection,
                baseExitNdc,
                correctedExitNdc,
                error,
                area,
                determinant,
                inverseDeterminant
        );
    }

    private static ProjectedQuad projectWorldPortal(Matrix4f viewProjection, PortalFrame portal) {
        Vec3[] corners = portalCorners(portal);
        return new ProjectedQuad(
                project(viewProjection, corners[0]),
                project(viewProjection, corners[1]),
                project(viewProjection, corners[2]),
                project(viewProjection, corners[3])
        );
    }

    private static ProjectedQuad projectExitPortal(
            Matrix4f projection,
            Vec3 cameraPosition,
            org.joml.Quaternionf cameraRotation,
            PortalFrame portal
    ) {
        Matrix4f viewProjection = new Matrix4f(projection)
                .mul(new Matrix4f().rotation(new org.joml.Quaternionf(cameraRotation).conjugate())
                        .translate(
                                (float) -cameraPosition.x(),
                                (float) -cameraPosition.y(),
                                (float) -cameraPosition.z()
                        ));
        return projectWorldPortal(viewProjection, portal);
    }

    private static Vec3[] portalCorners(PortalFrame portal) {
        Vec3 center = portal.position();
        Vec3 right = PortalFrameMath.right(portal).scale(-1.0D);
        Vec3 up = PortalFrameMath.up(portal);
        double halfWidth = portal.width() * 0.5D;
        double halfHeight = portal.height() * 0.5D;

        return new Vec3[] {
                center.subtract(right.scale(halfWidth)).subtract(up.scale(halfHeight)),
                center.add(right.scale(halfWidth)).subtract(up.scale(halfHeight)),
                center.add(right.scale(halfWidth)).add(up.scale(halfHeight)),
                center.subtract(right.scale(halfWidth)).add(up.scale(halfHeight))
        };
    }

    private static Corner project(Matrix4f viewProjection, Vec3 worldPosition) {
        Vector4f clip = new Vector4f(
                (float) worldPosition.x(),
                (float) worldPosition.y(),
                (float) worldPosition.z(),
                1.0F
        ).mul(viewProjection);

        if (Math.abs(clip.w) < 1.0E-5F) {
            return Corner.invalid(clip);
        }

        return new Corner(clip, new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w), true);
    }

    private static double[] homography(double[][] src, ProjectedQuad quad) {
        double[][] a = new double[8][9];
        Corner[] dst = {quad.bottomLeft(), quad.bottomRight(), quad.topRight(), quad.topLeft()};

        for (int i = 0; i < 4; i++) {
            double x = src[i][0];
            double y = src[i][1];
            double u = dst[i].ndc().x;
            double v = dst[i].ndc().y;
            int row = i * 2;

            a[row][0] = x;
            a[row][1] = y;
            a[row][2] = 1.0D;
            a[row][6] = -u * x;
            a[row][7] = -u * y;
            a[row][8] = u;

            a[row + 1][3] = x;
            a[row + 1][4] = y;
            a[row + 1][5] = 1.0D;
            a[row + 1][6] = -v * x;
            a[row + 1][7] = -v * y;
            a[row + 1][8] = v;
        }

        return solve(a);
    }

    private static Matrix4f homographyToClipMatrix(double[] h) {
        // Clip post-transform:
        // x' = h00*x + h01*y + h02*w
        // y' = h10*x + h11*y + h12*w
        // w' = h20*x + h21*y + w
        // z is preserved for now; this is diagnostic and depth behavior may need a later pass.
        return new Matrix4f(
                (float) h[0], (float) h[3], 0.0F, (float) h[6],
                (float) h[1], (float) h[4], 0.0F, (float) h[7],
                0.0F, 0.0F, 1.0F, 0.0F,
                (float) h[2], (float) h[5], 0.0F, 1.0F
        );
    }

    private static double[] invertHomography(double[] h) {
        double a = h[0];
        double b = h[1];
        double c = h[2];
        double d = h[3];
        double e = h[4];
        double f = h[5];
        double g = h[6];
        double i = h[7];
        double determinant = determinant(h);

        if (Math.abs(determinant) < 1.0E-10D) {
            throw new IllegalArgumentException("singular inverse homography");
        }

        double[] inverse = new double[] {
                (e - f * i) / determinant,
                (c * i - b) / determinant,
                (b * f - c * e) / determinant,
                (f * g - d) / determinant,
                (a - c * g) / determinant,
                (c * d - a * f) / determinant,
                (d * i - e * g) / determinant,
                (b * g - a * i) / determinant
        };
        double h22 = (a * e - b * d) / determinant;

        if (Math.abs(h22) < 1.0E-10D) {
            throw new IllegalArgumentException("invalid inverse homography normalization");
        }

        for (int index = 0; index < inverse.length; index++) {
            inverse[index] /= h22;
        }

        return inverse;
    }

    private static double determinant(double[] h) {
        double a = h[0];
        double b = h[1];
        double c = h[2];
        double d = h[3];
        double e = h[4];
        double f = h[5];
        double g = h[6];
        double i = h[7];
        return a * (e - f * i) - b * (d - f * g) + c * (d * i - e * g);
    }

    private static double[] solve(double[][] augmented) {
        int size = 8;

        for (int column = 0; column < size; column++) {
            int pivot = column;

            for (int row = column + 1; row < size; row++) {
                if (Math.abs(augmented[row][column]) > Math.abs(augmented[pivot][column])) {
                    pivot = row;
                }
            }

            if (Math.abs(augmented[pivot][column]) < 1.0E-10D) {
                throw new IllegalArgumentException("singular homography");
            }

            double[] swap = augmented[column];
            augmented[column] = augmented[pivot];
            augmented[pivot] = swap;

            double divisor = augmented[column][column];
            for (int c = column; c <= size; c++) {
                augmented[column][c] /= divisor;
            }

            for (int row = 0; row < size; row++) {
                if (row == column) {
                    continue;
                }

                double factor = augmented[row][column];
                for (int c = column; c <= size; c++) {
                    augmented[row][c] -= factor * augmented[column][c];
                }
            }
        }

        double[] result = new double[8];
        for (int row = 0; row < size; row++) {
            result[row] = augmented[row][size];
        }
        return result;
    }

    public record Result(
            boolean valid,
            String reason,
            Matrix4f correctionMatrix,
            Matrix4f correctedProjection,
            ProjectedQuad entranceNdc,
            ProjectedQuad correctedExitNdc,
            float cornerError,
            ProjectedQuad baseExitNdc,
            String direction,
            String multiplicationOrder,
            double determinant,
            double inverseDeterminant,
            float correctedArea
    ) {
        private static Result invalid(String reason, ProjectedQuad entrance, ProjectedQuad exit) {
            return new Result(false, reason, new Matrix4f(), new Matrix4f(), entrance, exit, Float.POSITIVE_INFINITY, exit, "n/a", "n/a", Double.NaN, Double.NaN, 0.0F);
        }

        public String correctionSummary() {
            return summarizeMatrix(this.correctionMatrix);
        }

        public String correctedProjectionSummary() {
            return summarizeMatrix(this.correctedProjection);
        }

        public String errorSummary() {
            if (!Float.isFinite(this.cornerError)) {
                return "invalid";
            }
            return String.format(Locale.ROOT, "%.4f", this.cornerError);
        }

        public String correctedAreaSummary() {
            return String.format(Locale.ROOT, "%.6f", this.correctedArea);
        }

        public String determinantSummary() {
            return String.format(Locale.ROOT, "%.6f/%.6f", this.determinant, this.inverseDeterminant);
        }
    }

    public record ProjectedQuad(Corner bottomLeft, Corner bottomRight, Corner topRight, Corner topLeft) {
        private static ProjectedQuad invalid() {
            return new ProjectedQuad(Corner.invalid(new Vector4f()), Corner.invalid(new Vector4f()), Corner.invalid(new Vector4f()), Corner.invalid(new Vector4f()));
        }

        public boolean valid() {
            return this.bottomLeft.valid()
                    && this.bottomRight.valid()
                    && this.topRight.valid()
                    && this.topLeft.valid();
        }

        public float maxXyError(ProjectedQuad other) {
            return Math.max(
                    Math.max(error(this.bottomLeft, other.bottomLeft), error(this.bottomRight, other.bottomRight)),
                    Math.max(error(this.topRight, other.topRight), error(this.topLeft, other.topLeft))
            );
        }

        public float ndcArea() {
            if (!this.valid()) {
                return 0.0F;
            }

            float area = 0.0F;
            Corner[] corners = {this.bottomLeft, this.bottomRight, this.topRight, this.topLeft};
            for (int index = 0; index < corners.length; index++) {
                Vector3f current = corners[index].ndc();
                Vector3f next = corners[(index + 1) % corners.length].ndc();
                area += current.x * next.y - next.x * current.y;
            }
            return Math.abs(area) * 0.5F;
        }

        public String ndcSummary() {
            if (!this.valid()) {
                return "invalid";
            }

            return String.format(
                    Locale.ROOT,
                    "BL %.2f,%.2f BR %.2f,%.2f TR %.2f,%.2f TL %.2f,%.2f",
                    this.bottomLeft.ndc().x,
                    this.bottomLeft.ndc().y,
                    this.bottomRight.ndc().x,
                    this.bottomRight.ndc().y,
                    this.topRight.ndc().x,
                    this.topRight.ndc().y,
                    this.topLeft.ndc().x,
                    this.topLeft.ndc().y
            );
        }

        public String clipSummary() {
            return "BL " + this.bottomLeft.clipSummary()
                    + " BR " + this.bottomRight.clipSummary()
                    + " TR " + this.topRight.clipSummary()
                    + " TL " + this.topLeft.clipSummary();
        }

        private static float error(Corner a, Corner b) {
            if (!a.valid() || !b.valid()) {
                return Float.POSITIVE_INFINITY;
            }

            float dx = a.ndc().x - b.ndc().x;
            float dy = a.ndc().y - b.ndc().y;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    public record Corner(Vector4f clip, Vector3f ndc, boolean valid) {
        private static Corner invalid(Vector4f clip) {
            return new Corner(new Vector4f(clip), new Vector3f(), false);
        }

        private String clipSummary() {
            return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", this.clip.x, this.clip.y, this.clip.z, this.clip.w);
        }
    }

    private static String summarizeMatrix(Matrix4f matrix) {
        int hash = 1;

        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                hash = 31 * hash + Float.floatToIntBits(matrix.get(column, row));
            }
        }

        return String.format(Locale.ROOT, "%08x", hash);
    }

    private record Candidate(
            boolean valid,
            String reason,
            String direction,
            String multiplicationOrder,
            Matrix4f correctionMatrix,
            Matrix4f correctedProjection,
            ProjectedQuad baseExitNdc,
            ProjectedQuad correctedExitNdc,
            float cornerError,
            float correctedArea,
            double determinant,
            double inverseDeterminant
    ) {
        private static Candidate invalid(String reason, Matrix4f baseProjection, ProjectedQuad baseExitNdc) {
            return new Candidate(false, reason, "n/a", "n/a", new Matrix4f(), new Matrix4f(baseProjection), baseExitNdc, ProjectedQuad.invalid(), Float.POSITIVE_INFINITY, 0.0F, Double.NaN, Double.NaN);
        }

        private Candidate withInvalidReason(String reason) {
            return new Candidate(false, reason, this.direction, this.multiplicationOrder, this.correctionMatrix, this.correctedProjection, this.baseExitNdc, this.correctedExitNdc, this.cornerError, this.correctedArea, this.determinant, this.inverseDeterminant);
        }
    }
}
