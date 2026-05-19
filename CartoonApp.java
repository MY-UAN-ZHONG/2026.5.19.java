import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import javax.imageio.ImageIO;

public class CartoonApp extends JFrame {

    private BufferedImage originalImage;
    private BufferedImage cartoonImage;
    
    // 左右兩個圖片顯示標籤
    private JLabel originalLabel;
    private JLabel cartoonLabel;
    
    // 控制參數的滑桿
    private JSlider blurSlider;
    private JSlider edgeSlider;
    private JSlider colorSlider;
    
    // 顯示目前數值的標籤
    private JLabel blurValueLabel;
    private JLabel edgeValueLabel;
    private JLabel colorValueLabel;

    public CartoonApp() {
        setTitle("Traditional Cartoon Style App (Interactive Dual-View)");
        setSize(1000, 650); // 加大視窗以容納左右兩張圖
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- 1. 中央圖片顯示區 (使用 GridLayout 左右並排) ---
        JPanel imagePanel = new JPanel(new GridLayout(1, 2, 10, 0));
        imagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        originalLabel = new JLabel("Original Image", SwingConstants.CENTER);
        originalLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        cartoonLabel = new JLabel("Cartoon Result", SwingConstants.CENTER);
        cartoonLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        imagePanel.add(new JScrollPane(originalLabel));
        imagePanel.add(new JScrollPane(cartoonLabel));
        add(imagePanel, BorderLayout.CENTER);

        // --- 2. 底部控制區 ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 左側：按鈕區
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadBtn = new JButton("Load Image");
        JButton saveBtn = new JButton("Save Image");
        buttonPanel.add(loadBtn);
        buttonPanel.add(saveBtn);
        bottomPanel.add(buttonPanel, BorderLayout.WEST);

        // 右側：滑桿參數區 (使用 BoxLayout 垂直排列)
        JPanel sliderPanel = new JPanel();
        sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.Y_AXIS));
        
        // 建立滑桿與標籤
        blurSlider = new JSlider(0, 15, 4);      // 高斯模糊 (0~15次，預設4)
        edgeSlider = new JSlider(0, 255, 120);   // 邊緣閾值 (0~255，預設120)
        colorSlider = new JSlider(2, 20, 5);     // 色彩階數 (2~20，預設5)

        blurValueLabel = new JLabel("Blur Iterations: " + blurSlider.getValue());
        edgeValueLabel = new JLabel("Edge Threshold: " + edgeSlider.getValue());
        colorValueLabel = new JLabel("Color Levels: " + colorSlider.getValue());

        sliderPanel.add(createSliderRow(blurValueLabel, blurSlider));
        sliderPanel.add(createSliderRow(edgeValueLabel, edgeSlider));
        sliderPanel.add(createSliderRow(colorValueLabel, colorSlider));
        
        bottomPanel.add(sliderPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- 3. 綁定事件 ---
        loadBtn.addActionListener(e -> loadImage());
        saveBtn.addActionListener(e -> saveImage());

        // 建立共用的滑桿監聽器
        ChangeListener sliderListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // 更新文字標籤
                blurValueLabel.setText("Blur Iterations: " + blurSlider.getValue());
                edgeValueLabel.setText("Edge Threshold: " + edgeSlider.getValue());
                colorValueLabel.setText("Color Levels: " + colorSlider.getValue());
                
                // getValueIsAdjusting() 確保只有在滑鼠放開時才進行運算，避免拖曳時嚴重卡頓
                if (!((JSlider)e.getSource()).getValueIsAdjusting() && originalImage != null) {
                    applyCartoonFilter();
                }
            }
        };

        blurSlider.addChangeListener(sliderListener);
        edgeSlider.addChangeListener(sliderListener);
        colorSlider.addChangeListener(sliderListener);
    }

    // 輔助方法：將標籤與滑桿組合在同一行
    private JPanel createSliderRow(JLabel label, JSlider slider) {
        JPanel panel = new JPanel(new BorderLayout());
        label.setPreferredSize(new Dimension(150, 20));
        panel.add(label, BorderLayout.WEST);
        panel.add(slider, BorderLayout.CENTER);
        return panel;
    }

    private void loadImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "jpg", "png", "jpeg"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                originalImage = ImageIO.read(file);
                // 顯示左側原圖
                updateImageDisplay(originalImage, originalLabel);
                // 載入後自動套用一次濾鏡顯示在右側
                applyCartoonFilter();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading image.");
            }
        }
    }

    private void saveImage() {
        if (cartoonImage == null) return;
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = new File(chooser.getSelectedFile().getAbsolutePath() + ".png");
                ImageIO.write(cartoonImage, "png", file);
                JOptionPane.showMessageDialog(this, "Image saved successfully!");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving image.");
            }
        }
    }

    private void updateImageDisplay(BufferedImage img, JLabel label) {
        // 根據 JLabel 目前的大小進行等比例縮放
        int targetWidth = label.getWidth() > 0 ? label.getWidth() : 450;
        Image scaledImg = img.getScaledInstance(targetWidth, -1, Image.SCALE_SMOOTH);
        label.setIcon(new ImageIcon(scaledImg));
        label.setText("");
    }

    // 實作管線處理 (Pipeline)
    private void applyCartoonFilter() {
        if (originalImage == null) return;

        // --- 從滑桿動態取得參數 ---
        int blurIterations = blurSlider.getValue();
        int edgeThreshold = edgeSlider.getValue();
        int colorLevels = colorSlider.getValue();

        // 1. 高斯模糊 (Gaussian Blur Smoothing)
        BufferedImage blurred = GaussianBlur.apply(originalImage, blurIterations);
        
        // 2. Sobel 邊緣擷取 (EdgeDetector)
        BufferedImage edges = EdgeDetector.detect(blurred, edgeThreshold);
        
        // 3. 色彩量化 (Color Quantizer)
        BufferedImage quantized = ColorQuantizer.quantize(blurred, colorLevels); 
        
        // 4. 渲染結合 (Cartoon Renderer)
        cartoonImage = CartoonRenderer.render(quantized, edges);
        
        // 更新右側成品圖
        updateImageDisplay(cartoonImage, cartoonLabel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new CartoonApp().setVisible(true);
        });
    }

    // ==========================================
    // 物件導向架構 (Object-Oriented Architecture) 模組
    // ==========================================

    static class GaussianBlur {
        public static BufferedImage apply(BufferedImage src, int iterations) {
            if (iterations <= 0) return src;
            float[] matrix = {
                1/16f, 2/16f, 1/16f,
                2/16f, 4/16f, 2/16f,
                1/16f, 2/16f, 1/16f
            };
            Kernel kernel = new Kernel(3, 3, matrix);
            ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            
            BufferedImage result = src;
            for(int i = 0; i < iterations; i++) {
                result = op.filter(result, null);
            }
            return result;
        }
    }

    static class EdgeDetector {
        public static BufferedImage detect(BufferedImage src, int threshold) {
            int width = src.getWidth();
            int height = src.getHeight();
            BufferedImage edges = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

            int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
            int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int pixelX = 0;
                    int pixelY = 0;

                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            int rgb = src.getRGB(x + j, y + i);
                            int gray = (int)(0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
                            pixelX += gray * sobelX[i + 1][j + 1];
                            pixelY += gray * sobelY[i + 1][j + 1];
                        }
                    }

                    int magnitude = (int) Math.min(255, Math.sqrt(pixelX * pixelX + pixelY * pixelY));
                    int edgeColor = magnitude > threshold ? 0 : 255; 
                    int newRgb = new Color(edgeColor, edgeColor, edgeColor).getRGB();
                    edges.setRGB(x, y, newRgb);
                }
            }
            return edges;
        }
    }

    static class ColorQuantizer {
        public static BufferedImage quantize(BufferedImage src, int levels) {
            int width = src.getWidth();
            int height = src.getHeight();
            BufferedImage quantized = new BufferedImage(width, height, src.getType());
            if (levels <= 0) levels = 1; 
            int step = 255 / levels;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color c = new Color(src.getRGB(x, y));
                    int r = Math.min(255, (c.getRed() / step) * step);
                    int g = Math.min(255, (c.getGreen() / step) * step);
                    int b = Math.min(255, (c.getBlue() / step) * step);
                    quantized.setRGB(x, y, new Color(r, g, b).getRGB());
                }
            }
            return quantized;
        }
    }

    static class CartoonRenderer {
        public static BufferedImage render(BufferedImage colorImage, BufferedImage edgeImage) {
            int width = colorImage.getWidth();
            int height = colorImage.getHeight();
            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int edgeColor = edgeImage.getRGB(x, y) & 0xFF; 
                    if (edgeColor == 0) { 
                        result.setRGB(x, y, Color.BLACK.getRGB());
                    } else { 
                        result.setRGB(x, y, colorImage.getRGB(x, y));
                    }
                }
            }
            return result;
        }
    }
}