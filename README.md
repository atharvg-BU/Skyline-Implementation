# CS562 Programming Assignment 1: Skyline Computation

This assignment adds two-dimensional skyline computation and incremental skyline updates to the [davidmoten R-tree implementation](https://github.com/davidmoten/rtree). The assignment application is [SkylineApp.java](rtree/src/main/java/skyline/SkylineApp.java), and [run-skyline.sh](run-skyline.sh) compiles and launches it. The original library documentation is available in [rtree/README.md](rtree/README.md).

## Skyline implementation

Smaller x and y coordinates are preferred. The skyline contains points that are not dominated by another point. For different coordinate pairs, a point dominates another when it is no larger in either coordinate and smaller in at least one. Equal coordinates are treated as duplicates for skyline output: inserting another record at an existing skyline location keeps the existing representative. Records with different IDs are still stored separately in the R-tree.

The application uses an `RTree<String, Point>` with a maximum of three children per node, a `HashSet` for skyline entries, and a priority queue for branch-and-bound traversal. Skyline output order is unspecified because it comes from a `HashSet`.

| Component | Purpose |
| --- | --- |
| `main` | Loads the dataset, assigns IDs, builds the tree, computes the initial skyline, and handles menu or command-line updates. It also validates input and retains the new tree returned by each insertion or deletion. |
| `QueueItem` | Stores a node or entry together with its priority. |
| `createSkyline` | Traverses the R-tree in increasing priority order. A point's priority is `x + y`; a node's priority is the sum of its minimum x and minimum y. Internal nodes expand into children, leaves expand into point entries, and undominated points enter the skyline. Queued items whose lower-left corners are dominated are pruned. |
| `insertSkyline` | Checks the new point against the current skyline. If it is dominated or has the same coordinates as a skyline representative, the skyline is unchanged. Otherwise, it safely removes dominated skyline entries with `removeIf` and adds the new entry. |
| `deleteSkyline` | Leaves the skyline unchanged when the deleted record is not a skyline representative. Otherwise, it removes that entry and calls the restricted replacement search. |
| `restrictedBBSSkyline` | Searches for replacements in the deleted point's dominance region, bounded by surviving skyline neighbors. It skips rectangles completely outside the region and explores rectangles that overlap it. Surviving skyline points and newly accepted replacements filter candidates. |

The caller updates the actual R-tree before updating the skyline. A dominated insertion still enters the database, since it may become a skyline point after a later deletion. Deleting a record passes the updated tree root to the replacement search. Deleting the final record is handled without accessing an absent root.

## Dataset format

Each nonblank line must contain exactly two finite numeric coordinates separated by spaces or tabs, without a header:

```text
1 9
3 2
9 1
```

Each record receives its original file line number as its string ID. Blank lines are skipped, so IDs can have gaps. The application reports malformed records with their line numbers. New records require a nonempty ID that is not already in use.

- **`Dataset_for_PA1`**: The test dataset provided by the instructor for Programming Assignment 1. It contains 38,377 two-dimensional points and is used to test skyline computation and updates on a larger dataset.
- **`skyline_dataset_points.txt`**: A smaller dataset created from the example in the lecture slides. It contains 13 two-dimensional points and is used to manually verify skyline results and demonstrate insertion and deletion behavior.

## Build and run

Run the commands below from the assignment folder containing `rtree/` and the dataset files.

A JDK and Maven are needed. Java 17 was used for development and testing. Set `JAVA_HOME` to your JDK directory if needed; otherwise, the launcher defaults to the development machine's Homebrew OpenJDK 17 location.

Build the library and download its dependencies before the first run:

```sh
mvn -f rtree/pom.xml -DskipTests package
```

The launcher expects `rtree/target/rtree-0.13-SNAPSHOT.jar` and the Guava Mini 0.1.7 and RxJava 1.3.8 JARs in the default local Maven repository. It compiles `SkylineApp.java` on each run.

Start the interactive menu with either dataset:

```sh
./run-skyline.sh skyline_dataset_points.txt
```

```sh
./run-skyline.sh Dataset_for_PA1
```

The initial point count and skyline are printed before the menu:

```text
1. View skyline
2. Insert point
3. Delete point
4. Exit
```

Insertion prompts for an ID, x, and y. Deletion prompts for the record ID. Both operations print the updated point count and skyline. Invalid menu choices, duplicate IDs, unknown deletion IDs, and invalid or non-finite coordinates are reported without ending the menu session.

For example, the 13-point dataset initially has skyline coordinates `{(1, 9), (3, 2), (9, 1)}`. Inserting ID `14` at `(2, 1)` changes the skyline to `{(1, 9), (2, 1)}` while retaining all 14 records in the database.

Updates can also be supplied as command-line arguments:

```sh
./run-skyline.sh skyline_dataset_points.txt --insert new1 2 1 --delete new1
```

These updates run in order and then exit without opening the menu. To delete the original record from line 9:

```sh
./run-skyline.sh skyline_dataset_points.txt --delete 9
```

Updates affect only the current process; the dataset file is not rewritten. Point counts and skyline results are displayed in the terminal.

## Validation performed

Development checks included compilation, loading all 38,377 assignment records, comparing skyline coordinates against an independent sorted-scan reference, menu input validation, insertion into an empty tree, and deletion of the final record. All 13 example records were deleted sequentially, checking skyline updates after each deletion. The eight original skyline records in the assignment dataset were also deleted sequentially, and every resulting skyline matched the independent reference.

These were development checks run separately from the repository's existing library test suite.

## AI assistance statement

I used Codex to understand the existing R-tree code and the roles of its classes, and to help write the shell script used to run the program. 
