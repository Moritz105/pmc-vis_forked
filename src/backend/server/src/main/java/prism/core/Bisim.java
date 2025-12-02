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

    public void bisimulate(Model model){
        
    }
}