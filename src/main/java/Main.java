import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    public static ArrayList<ArrayList<Integer>> read2DBinary(Scanner scnr)
    {
        ArrayList<ArrayList<Integer>> a = new ArrayList<>();
        scnr.nextLine(); //skip header
        while (scnr.hasNextLine()) {
            String[] line = scnr.nextLine().split("\t");


            ArrayList<Integer> line_list = new ArrayList<>();

            int first = 0;
            for (String x : line) {
                if(first == 0)
                {
                    first = 1;
                    continue;

                }
                line_list.add(Integer.valueOf(x));
            }

            a.add(line_list);
        }
        return a;
    }
    public static void main(String[] args) throws FileNotFoundException {
        String modelRunName = "Results0501_40criselpctunavailable";
        Scanner scnr = new Scanner(new File("data/a.csv"));

        ArrayList<ArrayList<Integer>> a = read2DBinary(scnr);

        //read in d
        scnr = new Scanner(new File("data/d.csv"));
        ArrayList<ArrayList<Integer>> d = read2DBinary(scnr);


        //read in c
        scnr = new Scanner(new File("data/c.csv"));
        ArrayList<ArrayList<Integer>> c = read2DBinary(scnr);


        //read in p
        scnr = new Scanner(new File("data/p.csv"));
        ArrayList<ArrayList<Integer>> p = read2DBinary(scnr);

        //read in w
        scnr = new Scanner(new File("data/w_rand_90.csv"));
        ArrayList<ArrayList<Integer>> w = read2DBinary(scnr);

        //start optimization model
        Loader.loadNativeLibraries();

        MPSolver solver = MPSolver.createSolver("CP_SAT");

        double infinity = java.lang.Double.POSITIVE_INFINITY;

        //calc set sizes
        int I = c.size();
        int J = w.size();
        int S = d.get(0).size();
        int K = a.get(0).size();

        //specify office hour "cohort capacity"
        int C = 3;


        //create vars
        ArrayList<ArrayList<MPVariable>> x = new ArrayList<>();
        for(int i = 0; i < I; i++)
        {
            //System.out.println(i);
            ArrayList<MPVariable> row = new ArrayList<>();
            for(int j = 0; j < J;j++)
            {
                row.add(solver.makeBoolVar(String.format("X_%s%s",i,j)));
            }
            x.add(row);
        }

        ArrayList<ArrayList<MPVariable>> y = new ArrayList<>();
        for(int s = 0; s < S; s++)
        {
            ArrayList<MPVariable> row = new ArrayList<>();
            for(int j = 0; j < J;j++)
            {
                row.add(solver.makeBoolVar(String.format("Y_%s%s",s,j)));
            }
            y.add(row);
        }

        //create constraints
        //constraint 1
        //(faculty can teach at most 1 class in a period)
        for (int j = 0; j < J; j++) {
            for (int k = 0; k < K; k++) {
                MPConstraint constraint1 = solver.makeConstraint(-infinity, 1, String.format("Constraint1_j%s_k%s", j, k));
                for (int i = 0; i < I; i++) {
                    constraint1.setCoefficient(x.get(i).get(j), a.get(i).get(k));
                }
            }
        }

        //constraint 2
        //(faculty must be OK with teaching in a period to be assigned)
        for (int i = 0; i < I; i++) {
            for (int j = 0; j < J; j++) {
                for (int k = 0; k < K; k++) {
                    MPConstraint constraint2 = solver.makeConstraint(0,w.get(j).get(k), String.format("Constraint2_i%s_j%s_k%s", i, j, k));
                    constraint2.setCoefficient(x.get(i).get(j),a.get(i).get(k));
                }
            }
        }


        //constraint 3
        // (no class in same degree plan semester can conflict)
        for (int s = 0; s < S; s++) {
            for (int j = 0; j < J; j++) {
                MPConstraint constraint3 = solver.makeConstraint(0, 1, String.format("Constraint3_s%s_j%s", s, j));
                for (int i = 0; i < I; i++) {
                    constraint3.setCoefficient(x.get(i).get(j), d.get(i).get(s));

                }
            }
        }

        //constraint 4
        // (an open common period must be between two required courses)
        for (int s = 0; s < S; s++) {
            for (int j = 2; j < J; j++) {
                MPConstraint constraint4 = solver.makeConstraint(0, infinity, String.format("Constraint4_s%s_j%s", s, j));
                for (int i = 0; i < I; i++) {
                    constraint4.setCoefficient(x.get(i).get(j), 0.5*d.get(i).get(s));
                    constraint4.setCoefficient(y.get(s).get(j-1),-1);

                    constraint4.setCoefficient(x.get(i).get(j - 2), 0.5*d.get(i).get(s));
                }
            }
        }

        //constraint 4b
        //0, 6, 7, 12 ineligible for common pd

        for(int s = 0; s < S;s++)
        {
            MPConstraint constraint4b = solver.makeConstraint(0,0,"Constraint4b");
            constraint4b.setCoefficient(y.get(s).get(0),1);
            constraint4b.setCoefficient(y.get(s).get(6),1);
            constraint4b.setCoefficient(y.get(s).get(7),1);
            constraint4b.setCoefficient(y.get(s).get(12),1);
        }


        //constraint 4c
        //every cohort must have a minimum of 1 open pd
        for(int s = 2; s < 7;s++)
        {
            MPConstraint constraint4c = solver.makeConstraint(1,infinity,"Constraint4b");
            for(int j = 0; j < J;j++)
            {
                constraint4c.setCoefficient(y.get(s).get(j),1);
            }
        }

        //constraint 5
        // (Faculty must be available in the common open period)
        for (int s = 0; s < S; s++) {
            for (int i = 0; i < I; i++) {
                for (int j = 0; j < J; j++) {
                    for (int k = 0; k < K; k++) {
                        MPConstraint constraint = solver.makeConstraint(-infinity, 1, String.format("Constraint5_s%s_i%s_j%s_k%s", s, i, j, k));
                        constraint.setCoefficient(x.get(i).get(j), d.get(i).get(s) * a.get(i).get(k));
                        constraint.setCoefficient(y.get(s).get(j), 1);
                    }
                }
            }
        }

        // Constraint 6
        //(prereq chain courses must occur at different times)
        for (int i = 0; i < I; i++) {
            for (int i_prime = 0; i_prime < I; i_prime++) {
                for (int j = 0; j < J; j++) {
                    MPConstraint constraint = solver.makeConstraint(-infinity, 1, String.format("Constraint6_i%s_i'%s_j%s", i, i_prime, j));
                    constraint.setCoefficient(x.get(i).get(j), c.get(i).get(i_prime));
                    constraint.setCoefficient(x.get(i_prime).get(j), c.get(i).get(i_prime));
                }
            }
        }

        // Constraint 7
        //(coreq chain courses must occur at different times)
        for (int i = 0; i < I; i++) {
            for (int i_prime = 0; i_prime < I; i_prime++) {
                for (int j = 0; j < J; j++) {
                    MPConstraint constraint = solver.makeConstraint(-infinity, 1, String.format("Constraint7_i%s_i'%s_j%s", i, i_prime, j));
                   constraint.setCoefficient(x.get(i).get(j), p.get(i).get(i_prime));
                    constraint.setCoefficient(x.get(i_prime).get(j), p.get(i).get(i_prime));
                }
            }
        }

        // Constraint 8: Common open periods during a period j should not exceed office hours physical capacity
        //(common open periods during a period j should not exceed office hours physical capacity)
        for (int j = 0; j < J; j++) {
            MPConstraint constraint = solver.makeConstraint(-infinity, C, String.format("Constraint8_j%s", j));
            for (int s = 0; s < S; s++) {
                constraint.setCoefficient(y.get(s).get(j), 1);
            }
        }

        // Constraint 9: All classes must be offered
        //(all classes must be offered)
        for (int i = 0; i < I; i++) {
            MPConstraint constraint = solver.makeConstraint(1, 1, String.format("Constraint9_i%s", i));
            for (int j = 0; j < J; j++) {
                constraint.setCoefficient(x.get(i).get(j), 1);
            }
        }

        //write objetive
        MPObjective obj = solver.objective();
        for(int s = 0; s < S;s++)
        {
            for(int j = 0; j < J;j++)
            {
                obj.setCoefficient(y.get(s).get(j),1);
            }
        }

        obj.setMaximization();

        // Solving the optimization problem
        MPSolver.ResultStatus resultStatus = solver.solve();
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            System.out.println("Optimal or feasible solution found.");
            System.out.println("Objective value = " + solver.objective().value());

            // Print out the values of x
            //read in class codes
            Scanner sc = new Scanner(new File("data/c.csv"));
            String[] class_codes = sc.nextLine().split("\t");
            class_codes = Arrays.copyOfRange(class_codes,1,class_codes.length);

            //read in periods
            sc = new Scanner(new File("data/w.csv"));
            sc.nextLine();
            ArrayList<String> pds = new ArrayList<>();
            while(sc.hasNextLine())
            {
                pds.add(sc.nextLine().split("\t")[0]);
            }


            //write to results md file
            try {
                BufferedWriter f_writer
                        = new BufferedWriter(new FileWriter(
                        String.format("results/%s.md",modelRunName)));

                f_writer.write(String.format("# %s\n\n",modelRunName));
                f_writer.write("**Definition of a Common Open Period:** Period where all students from degree plan semester *s* are available," +
                        " in a period immediately between two required courses, at a time where all of their faculty are also available. \n\n");

                for(int s = 2; s < S-1;s++) { //2 to negate freshman yr and last semester

                    f_writer.write(String.format("**Degree Plan Semester %s**\n\n",s+1));
                    //md table format
                    f_writer.write("| Period | Courses |\n");
                    f_writer.write("|---------|-----------|\n");

                    for (int j = 0; j < J; j++) {
                        f_writer.write("| " + pds.get(j) + " | ");
                        //print classes to csv out
                        for (int i = 0; i < I; i++) {
                            if ((x.get(i).get(j).solutionValue() > 0) &(d.get(i).get(s) > 0)) {
                                //System.out.println("x[" + class_codes[i] + "][" + pds.get(j) + "] = " + x.get(i).get(j).solutionValue());
                                f_writer.write(class_codes[i]);
                            }
                        }


                        //for (int s = 0; s < S; s++) {
                            if (y.get(s).get(j).solutionValue() > 0) {
                                f_writer.write( String.format("Semester%sCommon", s + 1));
                            }
                        //}
                        f_writer.write("|\n");
                    }
                }

                f_writer.write("\n");
                // Print out the values of y
                f_writer.write("Values of y:\n\n");
                for (int s = 2; s < S; s++) {
                    for (int j = 0; j < J; j++) {
                        if(y.get(s).get(j).solutionValue() > 0) {

                            f_writer.write("y[" + (s+1 )+ "][" + pds.get(j) + "] = " + y.get(s).get(j).solutionValue() + "\n\n");
                        }
                    }
                }
                f_writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else {
            System.out.println("The problem does not have an optimal or feasible solution.");
        }
    }
}
