package prism.core;
import com.google.common.math.BigIntegerMath;
import parser.State;
import parser.VarList;
import parser.ast.Expression;
import parser.ast.ModulesFile;
import parser.ast.RewardStruct;
import parser.type.Type;
import parser.type.TypeBool;
import parser.type.TypeDouble;
import parser.type.TypeInt;

import prism.api.*;
import prism.core.*;
import simulator.Choice;
import simulator.TransitionList;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.HashMap;

import javax.ws.rs.client.Client;
import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

public class Bisim{
    Model model;
    public Bisim(Model model) {
        this.model=model;
    }

    public void bisimulate(Graph graph){
        //Zuständen ihren Labels entsprechned in Blöcke einteilen
        //Map<int,List<State>> blocks = new Map<int,List<State>>();
        //for (State state in graph.getStates()){
            // eher über parserstates mittels utility updater, anwendung beim modelparser/-checker anschauen
            // model hat getlabels funktion

        //}
        //Für jede Aktion a:
        //  prüfe, ob jeder Zustand bei Ausführung von Aktion a in gleichen Block übergeht
        //      wenn verschiedene oder gar keine Transition -> splitte Blöcke
        // wann fertig?
    }
}