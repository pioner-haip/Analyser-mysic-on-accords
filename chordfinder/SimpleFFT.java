package chordfinder;

/**
 * Итеративное БПФ (Кули-Тьюки) по месту. Длина массивов должна быть степенью двойки.
 */
public class SimpleFFT {
    public static void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n == 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("Длина массива должна быть степенью двойки");
        }
        // Битовая реверсия индексов
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = real[i]; real[i] = real[j]; real[j] = tr;
                double ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
        }
        // Бабочки Кули-Тьюки
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double curWr = 1, curWi = 0;
                for (int k = 0; k < len / 2; k++) {
                    double ur = real[i + k], ui = imag[i + k];
                    double vr = real[i + k + len / 2] * curWr - imag[i + k + len / 2] * curWi;
                    double vi = real[i + k + len / 2] * curWi + imag[i + k + len / 2] * curWr;
                    real[i + k] = ur + vr;
                    imag[i + k] = ui + vi;
                    real[i + k + len / 2] = ur - vr;
                    imag[i + k + len / 2] = ui - vi;
                    double nextWr = curWr * wr - curWi * wi;
                    double nextWi = curWr * wi + curWi * wr;
                    curWr = nextWr;
                    curWi = nextWi;
                }
            }
        }
    }
}
