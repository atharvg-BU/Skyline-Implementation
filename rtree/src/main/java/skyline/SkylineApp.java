package skyline;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;

import javax.imageio.ImageIO;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.Leaf;
import com.github.davidmoten.rtree.Node;
import com.github.davidmoten.rtree.NonLeaf;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.HasGeometry;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;

/** Entry point for building a two-dimensional skyline algorithm. */
public final class SkylineApp {

    private SkylineApp() {
    }

    private static class QueueItem {
        final HasGeometry item;
        final double dist;
        
        QueueItem(HasGeometry item, double dist) {
            this.item = item;
            this.dist = dist;
        }
    }

    public static void main(String[] args) throws IOException {
        String usage = "Usage: java skyline.SkylineApp <dataset-file> "
                + "[--insert <id> <x> <y> | --delete <id>]...";
        if (args.length == 0) {
            System.err.println(usage);
            return;
        }
        if (args[0].equals("--help") || args[0].equals("-h")) {
            System.out.println(usage);
            System.out.println("Provide only a dataset file to open the interactive menu.");
            System.out.println("Dataset point IDs are their original line numbers.");
            System.out.println("Inserted point IDs must be unique. Updates run in argument order.");
            return;
        }

        RTree<String, Point> tree = RTree.maxChildren(3).create();
        java.util.Map<String, Entry<String, Point>> entries = new java.util.HashMap<>();
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(
                java.nio.file.Paths.get(args[0]), java.nio.charset.StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] coordinates = line.split("\\s+");
                if (coordinates.length != 2) {
                    throw new IOException("Expected two coordinates at line " + lineNumber);
                }
                try {
                    double x = Double.parseDouble(coordinates[0]);
                    double y = Double.parseDouble(coordinates[1]);
                    if (!Double.isFinite(x) || !Double.isFinite(y)) {
                        throw new IOException("Coordinates must be finite at line " + lineNumber);
                    }
                    Entry<String, Point> entry = com.github.davidmoten.rtree.Entries.entry(
                            Integer.toString(lineNumber), Geometries.point(x, y));
                    tree = tree.add(entry);
                    entries.put(entry.value(), entry);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid coordinate at line " + lineNumber, e);
                }
            }
        }

        // Compute the skyline after all dataset points have been inserted.
        PriorityQueue<QueueItem> heap = new PriorityQueue<>(
                Comparator.comparingDouble((QueueItem item) -> item.dist)
        );
        HashSet<Entry<String, Point>> skyline = new HashSet<>();
        createSkyline(heap, tree.root().isPresent() ? tree.root().get() : null, skyline);
        System.out.println("Points in the R-tree: " + tree.size());
        System.out.println("Skyline: " + skyline);

        int argument = 1;
        while (argument < args.length) {
            String operation = args[argument++];

            // Insert into the database before updating the skyline.
            if (operation.equals("--insert")) {
                if (args.length - argument < 3) {
                    throw new IOException("--insert requires an ID and two coordinates. " + usage);
                }
                String id = args[argument++];
                if (id.trim().isEmpty() || entries.containsKey(id)) {
                    throw new IOException("Inserted point ID must be nonempty and unique: " + id);
                }
                double x;
                double y;
                try {
                    x = Double.parseDouble(args[argument++]);
                    y = Double.parseDouble(args[argument++]);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid coordinates for inserted point " + id, e);
                }
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    throw new IOException("Inserted coordinates must be finite: " + id);
                }
                Entry<String, Point> entry = com.github.davidmoten.rtree.Entries.entry(
                        id, Geometries.point(x, y));
                tree = tree.add(entry);
                entries.put(id, entry);
                insertSkyline(skyline, entry);
                System.out.println("Inserted point " + id + " at (" + x + ", " + y + ")");

            // Delete from the database and search for replacements using the updated root.
            } else if (operation.equals("--delete")) {
                if (argument >= args.length) {
                    throw new IOException("--delete requires a point ID. " + usage);
                }
                String id = args[argument++];
                Entry<String, Point> entry = entries.get(id);
                if (entry == null) {
                    throw new IOException("Unknown point ID: " + id);
                }
                tree = tree.delete(entry);
                entries.remove(id);
                Node<String, Point> root = tree.root().isPresent() ? tree.root().get() : null;
                deleteSkyline(skyline, root, entry, heap);
                System.out.println("Deleted point " + id);
            } else {
                throw new IOException("Unknown operation: " + operation + ". " + usage);
            }

            System.out.println("Points in the R-tree: " + tree.size());
            System.out.println("Skyline: " + skyline);
        }

        // With no command-line updates, let the user work with the loaded database.
        if (args.length == 1) {
            java.util.Scanner console = new java.util.Scanner(System.in);
            System.out.println("Dataset point IDs are their original line numbers.");
            System.out.println("Changes apply only to this session.");
            boolean running = true;
            while (running) {
                System.out.println();
                System.out.println("1. View skyline");
                System.out.println("2. Insert point");
                System.out.println("3. Delete point");
                System.out.println("4. Exit");
                System.out.print("Choose an option: ");
                if (!console.hasNextLine()) {
                    break;
                }
                String choice = console.nextLine().trim();

                if (choice.equals("1")) {
                    System.out.println("Points in the R-tree: " + tree.size());
                    System.out.println("Skyline: " + skyline);
                } else if (choice.equals("2")) {
                    System.out.print("Point ID: ");
                    if (!console.hasNextLine()) {
                        break;
                    }
                    String id = console.nextLine().trim();
                    if (id.isEmpty() || entries.containsKey(id)) {
                        System.out.println("Point ID must be nonempty and unique.");
                        continue;
                    }
                    System.out.print("X coordinate: ");
                    if (!console.hasNextLine()) {
                        break;
                    }
                    String xInput = console.nextLine().trim();
                    System.out.print("Y coordinate: ");
                    if (!console.hasNextLine()) {
                        break;
                    }
                    String yInput = console.nextLine().trim();
                    double x;
                    double y;
                    try {
                        x = Double.parseDouble(xInput);
                        y = Double.parseDouble(yInput);
                    } catch (NumberFormatException e) {
                        System.out.println("Coordinates must be valid numbers.");
                        continue;
                    }
                    if (!Double.isFinite(x) || !Double.isFinite(y)) {
                        System.out.println("Coordinates must be finite numbers.");
                        continue;
                    }
                    Entry<String, Point> entry = com.github.davidmoten.rtree.Entries.entry(
                            id, Geometries.point(x, y));
                    tree = tree.add(entry);
                    entries.put(id, entry);
                    insertSkyline(skyline, entry);
                    System.out.println("Inserted point " + id + " at (" + x + ", " + y + ")");
                    System.out.println("Points in the R-tree: " + tree.size());
                    System.out.println("Skyline: " + skyline);
                } else if (choice.equals("3")) {
                    System.out.print("Point ID to delete: ");
                    if (!console.hasNextLine()) {
                        break;
                    }
                    String id = console.nextLine().trim();
                    Entry<String, Point> entry = entries.get(id);
                    if (entry == null) {
                        System.out.println("Unknown point ID: " + id);
                        continue;
                    }
                    tree = tree.delete(entry);
                    entries.remove(id);
                    Node<String, Point> root = tree.root().isPresent() ? tree.root().get() : null;
                    deleteSkyline(skyline, root, entry, heap);
                    System.out.println("Deleted point " + id);
                    System.out.println("Points in the R-tree: " + tree.size());
                    System.out.println("Skyline: " + skyline);
                } else if (choice.equals("4")) {
                    running = false;
                } else {
                    System.out.println("Invalid option. Choose 1, 2, 3, or 4.");
                }
            }
        }

        if (tree.root().isPresent()) {
            saveLabeledTree(tree);
        }
    }

    private static void saveLabeledTree(RTree<String, Point> tree) throws IOException {
        final int size = 800;
        Rectangle bounds = tree.mbr().get();
        Rectangle view = Geometries.rectangle(bounds.x1() - 1, bounds.y1() - 1,
                bounds.x2() + 1, bounds.y2() + 1);
        BufferedImage treeImage = tree.visualize(size, size, view).createImage();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            // Flip the bounding boxes so y increases upward; draw labels upright afterward.
            graphics.drawImage(treeImage, 0, 0, size, size, 0, size, size, 0, null);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            for (Entry<String, Point> entry : tree.entries().toBlocking().toIterable()) {
                Point point = entry.geometry();
                int x = (int) Math.round((point.x() - view.x1())
                        / (view.x2() - view.x1()) * size);
                int y = size - 1 - (int) Math.round((point.y() - view.y1())
                        / (view.y2() - view.y1()) * size);
                graphics.setColor(Color.BLACK);
                graphics.fillOval(x - 6, y - 6, 12, 12);
                int labelWidth = graphics.getFontMetrics().stringWidth(entry.value());
                graphics.setColor(Color.WHITE);
                graphics.fillRect(x + 8, y - 24, labelWidth + 6, 20);
                graphics.setColor(Color.BLACK);
                graphics.drawString(entry.value(), x + 11, y - 9);
            }
        } finally {
            graphics.dispose();
        }
        File output = new File("target/rtree.png");
        output.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", output);
        System.out.println("Saved labeled R-tree: " + output);
    }

    public static void createSkyline(
            PriorityQueue<QueueItem> pq,
            Node<String, Point> node,
            HashSet<Entry<String, Point>> skyline) {

        if (node == null) {
            return;
        }

        double rootDist = node.geometry().mbr().x1() + node.geometry().mbr().y1();
        pq.add(new QueueItem(node, rootDist));

        while (!pq.isEmpty()) {
            HasGeometry item = pq.poll().item;

            if (item instanceof NonLeaf) {
                NonLeaf<String, Point> parent = (NonLeaf<String, Point>) item;
                for (Node<String, Point> child : parent.children()) {
                    double dist = child.geometry().mbr().x1()
                            + child.geometry().mbr().y1();
                    pq.add(new QueueItem(child, dist));
                }
            } else if (item instanceof Leaf) {
                Leaf<String, Point> leaf = (Leaf<String, Point>) item;
                for (Entry<String, Point> entry : leaf.entries()) {
                    double dist = entry.geometry().mbr().x1()
                            + entry.geometry().mbr().y1();
                    pq.add(new QueueItem(entry, dist));
                }
            } else if (item instanceof Entry) {
                // Add your skyline dominance checks for this entry here.
                Entry<String, Point> pt = (Entry<String, Point>) item;
                double xPt = pt.geometry().x();
                double yPt = pt.geometry().y();
                boolean dominated = false;

                for (Entry<String, Point> e : skyline) {
                    Point p = e.geometry();

                    if (p.x() <= xPt && p.y() <= yPt
                            && (p.x() < xPt || p.y() < yPt)) {
                        dominated = true;
                        break;
                    }
                }

                if (dominated) {
                    continue;
                }
                pq.removeIf(it -> {
                    Rectangle bounds = it.item.geometry().mbr();
                    return bounds.x1() >= xPt && bounds.y1() >= yPt;
                });
                if (!skyline.isEmpty()) {
                    for (Entry<String, Point> e : skyline) {
                        double xp = e.geometry().x();
                        double yp = e.geometry().y();
 
                        pq.removeIf(it -> {
                            Rectangle bounds = it.item.geometry().mbr();
                            return bounds.x1() >= xp && bounds.y1() >= yp;
                        });

                    }
                }
                skyline.add(pt);
            }
        }
    }

    public static void insertSkyline(HashSet<Entry<String, Point>> skyline, Entry<String, Point> entry){
        for(Entry<String, Point> e : skyline){
            if(e.geometry().x() <= entry.geometry().x() && e.geometry().y() <= entry.geometry().y()){
                return;
            }
        }

        skyline.removeIf(e ->
            e.geometry().x() >= entry.geometry().x()
            && e.geometry().y() >= entry.geometry().y()
        );

        skyline.add(entry);
    }

    public static void deleteSkyline(HashSet<Entry<String, Point>> skyline, Node<String, Point> node, Entry<String, Point> entry,PriorityQueue<QueueItem> pq){
        boolean isSkyline=false;
        for(Entry<String, Point> e : skyline){
            if(e.equals(entry)){
                isSkyline=true;
            }
        }
        if(!isSkyline){
            return;
        }
        skyline.remove(entry);
        restrictedBBSSkyline(node,entry, skyline);
    }

    public static void restrictedBBSSkyline(
            Node<String, Point> node,
            Entry<String, Point> entry,
            HashSet<Entry<String, Point>> skyline) {

        if (node == null) {
            return;
        }
        PriorityQueue<QueueItem> pq = new PriorityQueue<>(
                Comparator.comparingDouble((QueueItem item) -> item.dist)
        );

        double xmin=0.0,xmax=0.0,ymin=0.0,ymax=0.0;
        xmin=entry.geometry().x();
        ymin=entry.geometry().y();
        xmax=Double.POSITIVE_INFINITY;
        ymax=Double.POSITIVE_INFINITY;

        double left = Double.POSITIVE_INFINITY, right = Double.POSITIVE_INFINITY;

        for(Entry<String, Point> e : skyline){
            if(e.geometry().x() > xmin && (e.geometry().x()-xmin) < right){
                xmax=e.geometry().x();
                right=(e.geometry().x()-xmin);
            }

            if(e.geometry().x() < xmin && (xmin-e.geometry().x()) < left){
                ymax=e.geometry().y();
                left = xmin - e.geometry().x();
            }
        }

        double rootDist = node.geometry().mbr().x1() + node.geometry().mbr().y1();
        pq.add(new QueueItem(node, rootDist));

        while (!pq.isEmpty()) {
            HasGeometry item = pq.poll().item;
            if((item.geometry().mbr().x2()<xmin || item.geometry().mbr().x1()>xmax ||
            item.geometry().mbr().y2()<ymin || item.geometry().mbr().y1()>ymax)){
                continue;
            }

            if (item instanceof NonLeaf) {
                NonLeaf<String, Point> parent = (NonLeaf<String, Point>) item;
                for (Node<String, Point> child : parent.children()) {
                    double dist = child.geometry().mbr().x1()
                            + child.geometry().mbr().y1();
                    pq.add(new QueueItem(child, dist));
                }
            } else if (item instanceof Leaf) {
                Leaf<String, Point> leaf = (Leaf<String, Point>) item;
                for (Entry<String, Point> ent : leaf.entries()) {
                    double dist = ent.geometry().mbr().x1()
                            + ent.geometry().mbr().y1();
                    pq.add(new QueueItem(ent, dist));
                }
            } else if (item instanceof Entry) {
                // Add your skyline dominance checks for this entry here.
                Entry<String, Point> pt = (Entry<String, Point>) item;
                double xPt = pt.geometry().x();
                double yPt = pt.geometry().y();
                boolean dominated = false;

                for (Entry<String, Point> e : skyline) {
                    Point p = e.geometry();

                    if (p.x() <= xPt && p.y() <= yPt
                            && (p.x() < xPt || p.y() < yPt)) {
                        dominated = true;
                        break;
                    }
                }

                if (dominated) {
                    continue;
                }
                pq.removeIf(it -> {
                    Rectangle bounds = it.item.geometry().mbr();
                    return bounds.x1() >= xPt && bounds.y1() >= yPt;
                });
                if (!skyline.isEmpty()) {
                    for (Entry<String, Point> e : skyline) {
                        double xp = e.geometry().x();
                        double yp = e.geometry().y();
 
                        pq.removeIf(it -> {
                            Rectangle bounds = it.item.geometry().mbr();
                            return bounds.x1() >= xp && bounds.y1() >= yp;
                        });

                    }
                }
                skyline.add(pt);
            }
        }
    }
}
