package exec;

import exec.utils.Experiment;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import mcts.MCTSController;
import mcts.entries.ghosts.MCTSGhosts;
import pacman.controllers.Controller;
import pacman.controllers.ICEP_IDDFS;
import pacman.controllers.examples.StarterGhosts;
import pacman.controllers.examples.StarterPacMan;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;


enum Option {
    PACMAN_CLASS("pacman-class"),
    PACMAN_TIME("pacman-time"),
    PACMAN_SIMULATION_DEPTH("pacman-simdepth"),
    PACMAN_UCB_COEF("pacman-ucb-coef"),
    PACMAN_RANDOM_PROB("pacman-random-prob"),
    PACMAN_DEATH_WEIGHT("pacman-death-weight"),
    GHOST_CLASS("ghost-class"),
    GHOST_TIME("ghost-time"),
    GHOST_SIMULATION_DEPTH("ghost-simdepth"),
    GHOST_UCB_COEF("ghost-ucb-coef"),
    GHOST_RANDOM_PROB("ghost-random-prob"),
    GHOST_DEATH_WEIGHT("ghost-death-weight"),
    TRIAL_NO("trial-no"),
    VISUAL("visual", LongOpt.NO_ARGUMENT),
    HEADER("header", LongOpt.NO_ARGUMENT);

    private LongOpt longopt;
    public final static LongOpt LONG_OPTIONS[];
    static {
        Option options[] = Option.values();
        LONG_OPTIONS = new LongOpt[options.length];
        for (int i=0; i<options.length; i++) {
            LONG_OPTIONS[i] = options[i].getLongopt();
        }
    }

    Option(String name) {
        this(name, LongOpt.REQUIRED_ARGUMENT);
    }

    Option(String name, int has_arg) {
        longopt = new LongOpt(name, has_arg, null, this.ordinal());
    }

    /**
     * @return the longopt
     */
    public LongOpt getLongopt() {
        return longopt;
    }

    /**
     * @param longopt the longopt to set
     */
    public void setLongopt(LongOpt longopt) {
        this.longopt = longopt;
    }

}

public class ExecExperiment {
    private final static int DEFAULT_SIMULATION_DEPTH = 120;
    private final static double DEFAULT_UCB_COEF = 0.3;
    private final static double DEFAULT_RANDOM_PROB = 1;
    private final static double DEFAULT_DEATH_WEIGHT = 0.0;
    private final static String[] CONTROLLER_PACKAGES = {
        "pacman.controllers.examples",
        "mcts.entries.pacman",
        "mcts.entries.ghosts",
        "pacman.controllers"
    };

    private static Class lookupClass(String className) throws ClassNotFoundException {
        if (className.contains(".")) {
            return Class.forName(className);
        } else {
            for (String packageName: CONTROLLER_PACKAGES) {
                try {
                    return Class.forName(String.format("%s.%s", packageName, className));
                } catch (ClassNotFoundException ex) {
                    continue;
                }
            }
            throw new ClassNotFoundException(className);
        }
    }

    private static boolean isDefault(String param) {
        return param.toLowerCase().equals("default");
    }

    @SuppressWarnings("unchecked")
    private static <T> Controller<T> buildController(Class c, int simulationDepth, double ucbCoef, double randomProb, double deathWeight)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Constructor constructor = c.getConstructor();
        Controller<T> controller = (Controller<T>)constructor.newInstance();

        if (controller instanceof MCTSController) {
            MCTSController mctsController = (MCTSController)controller;
            mctsController.setSimulationDepth(simulationDepth);
            mctsController.setUcbCoef(ucbCoef);
            mctsController.setRandomSimulationMoveProbability(randomProb);
            mctsController.setDeathWeight(deathWeight);

        }

        return controller;
    }

    private static void printControllerHeader(String prefix, Controller controller) {
        System.out.printf("%sclass\t%stime\t", prefix, prefix);
        if (controller instanceof MCTSController) {
            System.out.printf("%ssim_depth\t%sucb_coef\t%sdeath_weight\t%savg_decision_sims\t%ssims_per_sec\t",
                              prefix, prefix, prefix, prefix, prefix);
        }
    }

    private static void printHeader(Controller<MOVE> pacmanController, Controller<EnumMap<GHOST,MOVE>> ghostController) {
        System.out.printf("trial\t");
        printControllerHeader("pacman_", pacmanController);
        printControllerHeader("ghost_", ghostController);
        System.out.printf("score\n");
    }

    private static void printControllerInfo(Controller controller, int time) {
        System.out.printf("%s\t%s\t", controller.getClass().getSimpleName(), time);
        if (controller instanceof MCTSController) {
            MCTSController mctsController = (MCTSController)controller;
            System.out.printf("%s\t%s\t%s\t%s\t%s\t",
                             mctsController.getSimulationDepth(), mctsController.getUcbCoef(),
                             mctsController.getDeathWeight(), mctsController.averageDecisionSimulations(),
                             mctsController.simulationsPerSecond());
        }
    }

    private static void printResults(int trialNo, Experiment experiment, Controller<MOVE> pacmanController,
                                     Controller<EnumMap<GHOST,MOVE>> ghostController, Game result) {
        System.out.printf("%s\t", trialNo);
        printControllerInfo(pacmanController, experiment.getPacmanDelay());
        printControllerInfo(ghostController, experiment.getGhostDelay());
        System.out.printf("%s\n", result.getScore());
    }

    public static void main(String[] args) throws Exception {
        int trialNo = 1;
        boolean visual = false;
        boolean header = false;

        Class pacmanClass = StarterPacMan.class;
        int pacmanSimulationDepth = DEFAULT_SIMULATION_DEPTH;
        double pacmanUcbCoef = DEFAULT_UCB_COEF;
        double pacmanRandomProb = DEFAULT_RANDOM_PROB;
        double pacmanDeathWeight = DEFAULT_DEATH_WEIGHT;

        Class ghostClass = StarterGhosts.class;
        int ghostSimulationDepth = DEFAULT_SIMULATION_DEPTH;
        double ghostUcbCoef = DEFAULT_UCB_COEF;
        double ghostRandomProb = DEFAULT_RANDOM_PROB;
        Experiment experiment = new Experiment();
        double ghostDeathWeight = DEFAULT_DEATH_WEIGHT;

        Getopt getopt = new Getopt(ExecDummyGhosts.class.getSimpleName(), args, "", Option.LONG_OPTIONS);
        int c;
        String arg;

        while ((c = getopt.getopt())!=-1) {
            Option option = Option.values()[c];

            switch (option) {
                case PACMAN_CLASS:
                    if (!isDefault(getopt.getOptarg())) {
                        pacmanClass = lookupClass(getopt.getOptarg());
                    }
                    break;
                case PACMAN_TIME:
                    if (!isDefault(getopt.getOptarg())) {
                        experiment.setPacmanDelay(Integer.parseInt(getopt.getOptarg()));
                    }
                    break;
                case PACMAN_SIMULATION_DEPTH:
                    if (!isDefault(getopt.getOptarg())) {
                        pacmanSimulationDepth = Integer.parseInt(getopt.getOptarg());
                    }
                    break;
                case PACMAN_UCB_COEF:
                    pacmanUcbCoef = Double.parseDouble(getopt.getOptarg());
                    break;
                case PACMAN_RANDOM_PROB:
                    pacmanRandomProb = Double.parseDouble(getopt.getOptarg());
                    break;
                case PACMAN_DEATH_WEIGHT:
                    pacmanDeathWeight = Double.parseDouble(getopt.getOptarg());
                    break;
                case GHOST_CLASS:
                    ghostClass = lookupClass(getopt.getOptarg());
                    break;
                case GHOST_TIME:
                    experiment.setGhostDelay(Integer.parseInt(getopt.getOptarg()));
                    break;
                case GHOST_SIMULATION_DEPTH:
                    ghostSimulationDepth = Integer.parseInt(getopt.getOptarg());
                    break;
                case GHOST_UCB_COEF:
                    ghostUcbCoef = Double.parseDouble(getopt.getOptarg());
                    break;
                case GHOST_RANDOM_PROB:
                    ghostRandomProb = Double.parseDouble(getopt.getOptarg());
                    break;
                case GHOST_DEATH_WEIGHT:
                    ghostDeathWeight = Double.parseDouble(getopt.getOptarg());
                    break;
                case VISUAL:
                    visual = true;
                    break;
                case HEADER:
                    header = true;
                    break;
                case TRIAL_NO:
                    trialNo = Integer.parseInt(getopt.getOptarg());
                    break;
                default:
                    System.err.printf("Unhandled switch: %s\n", option.getLongopt().getFlag());
                    System.exit(1);
                    break;
            }
        }

        Controller<MOVE> pacmanController = buildController(pacmanClass, pacmanSimulationDepth, pacmanUcbCoef, pacmanRandomProb, pacmanDeathWeight);
        Controller<EnumMap<GHOST,MOVE>> ghostController = buildController(ghostClass, ghostSimulationDepth, ghostUcbCoef, ghostRandomProb, ghostDeathWeight);

        if (header) {
            printHeader(pacmanController, ghostController);
        } else {
            experiment.setVisual(visual);
            experiment.setPacmanController(pacmanController);
            experiment.setGhostController(ghostController);

            Game result = experiment.execute();
            printResults(trialNo, experiment, pacmanController, ghostController, result);
        }
    }
}