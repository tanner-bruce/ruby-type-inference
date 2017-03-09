package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RSignatureContract {

    private int counter = 0;
    private int mySize = 0;

    private RSignatureContractNode startContractNode;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;

    private List<List<RSignatureContractNode>> levels;
    private RSignatureContractNode termNode;

    private RSignatureContractNode createNodeAndAddToLevels(Integer index) {
        RSignatureContractNode newNode;

        if (index < mySize)
            newNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.argNode);
        else
            newNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.returnNode);

        while (levels.size() <= index)
            levels.add(new ArrayList<>());
        levels.get(index).add(newNode);
        return newNode;
    }

    public int getCounter() {
        return counter;
    }

    public List<ParameterInfo> getParamInfoList() {
        return myArgsInfo;
    }

    private RSignatureContractNode getTermNode() {
        return termNode;
    }

    public RSignatureContract(RSignature signature) {
        this.mySize = signature.getArgsInfo().size();

        this.levels = new ArrayList<>();
        this.termNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.returnTypeNode);
        this.startContractNode = this.createNodeAndAddToLevels(0);
        this.myArgsInfo = signature.getArgsInfo();

        this.addRSignature(signature);
    }

    public RSignatureContractNode getStartNode() {
        return startContractNode;
    }


    public void addRSignature(RSignature signature) {
        this.counter++;
        RSignatureContractNode currNode = this.startContractNode;
        int currParamId = 1;

        String returnType = signature.getReturnTypeName();

        RSignatureContractNode termNode = getTermNode();

        for (String type : signature.getArgsTypes()) {

            int tempMask = 0;
            for (int j = (currParamId - 1); j < signature.getArgsInfo().size(); j++) {
                tempMask <<= 1;
                if (signature.getArgsTypes().get(j).equals(type)) {
                    tempMask |= 1;
                }
            }

            if (returnType.equals(type)) {
                tempMask <<= 1;
                tempMask |= 1;
            }

            TypedContractTransition transition = new TypedContractTransition(type);

            if (currNode.goByTypeSymbol(transition) == null) {
                RSignatureContractNode newNode;

                newNode = this.createNodeAndAddToLevels(currParamId);

                currNode.addLink(transition, newNode);

                newNode.setMask(tempMask);
                currNode = newNode;
            } else {
                currNode = currNode.goByTypeSymbol(transition);
                currNode.updateMask(tempMask);
            }
            currParamId++;
        }

        currNode.addLink(new TypedContractTransition(returnType), termNode);
    }

    public void minimization() {
        int numberOfLevels = levels.size();

        for (int i = numberOfLevels - 1; i > 0; i--) {
            List<RSignatureContractNode> level = levels.get(i);

            HashMap<RSignatureContractNode, RSignatureContractNode> representatives = new HashMap<>();
            List<Integer> uselessVertices = new ArrayList<>();

            for (RSignatureContractNode node : level) {
                representatives.put(node, node);
            }

            for (int v1 = 0; v1 < level.size(); v1++) {
                for (int v2 = v1 + 1; v2 < level.size(); v2++) {
                    RSignatureContractNode vertex1 = level.get(v1);
                    RSignatureContractNode vertex2 = level.get(v2);

                    boolean isSame = vertex1.getTypeTransitions().size() == vertex2.getTypeTransitions().size();

                    for (ContractTransition transition : vertex1.getTransitionKeys()) {

                        if (vertex1.goByTypeSymbol(transition) != vertex2.goByTypeSymbol(transition)) {
                            isSame = false;
                        }
                    }

                    if (isSame) {
                        RSignatureContractNode vertex1presenter = representatives.get(vertex1);
                        representatives.put(vertex2, vertex1presenter);
                        uselessVertices.add(v2);
                    }
                }
            }

            List<RSignatureContractNode> prevLevel = levels.get(i - 1);


            if (uselessVertices.size() > 0) {
                for (RSignatureContractNode node : prevLevel) {
                    for (ContractTransition transition : node.getTransitionKeys()) {
                        RSignatureContractNode child = node.goByTypeSymbol(transition);
                        node.addLink(transition, representatives.get(child));
                    }
                }
            }
            for (int index : uselessVertices) {
                if (this.levels.get(i).size() > index)
                    this.levels.get(i).remove(index);
            }
        }
    }


//    public List<String> getStringPresentation() {
//        List<String> answer = new ArrayList<>();
//
//        Queue<javafx.util.Pair<RSignatureContractNode, String>> tmp = new ArrayDeque<>();
//        tmp.add(new javafx.util.Pair<>(this.startContractNode, null));
//
//        while (!tmp.isEmpty())
//        {
//            RSignatureContractNode currNode = tmp.peek().getKey();
//            String currString = tmp.peek().getValue();
//
//            tmp.remove();
//
//            if (currNode.getNodeType() == RSignatureContractNode.ContractNodeType.returnNode)
//            {
//                StringJoiner joiner = new StringJoiner("|");
//
//                for (String transitionType : currNode.getTransitionKeys()) {
//                    joiner.add(transitionType);
//                }
//                if (currString == null)
//                    answer.add("()" + " -> " + joiner.toString());
//                else
//                    answer.add("(" + currString + ")" + " -> " + joiner.toString());
//                continue;
//            }
//
//            Map <RSignatureContractNode, String> newVertexes = new HashMap<>();
//
//            for (String transitionType : currNode.getTransitionKeys()) {
//                RSignatureContractNode tmpNode = currNode.goByTypeSymbol(transitionType);
//
//                if(newVertexes.containsKey(tmpNode))
//                {
//                    String oldValue = newVertexes.get(tmpNode);
//                    newVertexes.remove(tmpNode);
//                    newVertexes.put(tmpNode, oldValue + "|" + transitionType);
//                }
//                else
//                {
//                    if(transitionType.equals("no arguments"))
//                        newVertexes.put(tmpNode, "");
//                    else {
//                        if (currString == null)
//                            newVertexes.put(tmpNode, transitionType);
//                        else
//                            newVertexes.put(tmpNode, currString + ";" + transitionType);
//                    }
//                }
//            }
//            for (RSignatureContractNode node : newVertexes.keySet()) {
//                tmp.add(new javafx.util.Pair<>(node, newVertexes.get(node)));
//            }
//        }
//
//        return answer;
//    }

    public void compression() {
        compressionDFS(getStartNode(), 0);
        minimization();
    }

    private void compressionDFS(RSignatureContractNode node, int level) {
        int commonMask = -1;

        if (node.getTypeTransitions() != null) {
            for (ContractTransition transition : node.getTransitionKeys()) {
                commonMask &= node.goByTypeSymbol(transition).getMask();
            }

            if (commonMask > 0 && node.getTransitionKeys().size() > 1) {
                for (ContractTransition transition : node.getTransitionKeys()) {
                    updateSubtreeTypeDFS(node.goByTypeSymbol(transition), commonMask >> 1, level, level + 1, transition);
                }
            }
            for (ContractTransition transition : node.getTransitionKeys()) {
                compressionDFS(node.goByTypeSymbol(transition), level + 1);
            }
        }
    }

    private void updateSubtreeTypeDFS(RSignatureContractNode node, int mask, int parentLevel, int level, ContractTransition transition) {

        if (mask % 2 == 1) {
            ReferenceContractTransition newTransition = new ReferenceContractTransition(parentLevel);

            node.setReferenceLinks();
            node.changeTransitionType(transition, newTransition);
        }

        if (node.getTypeTransitions() != null) {
            for (ContractTransition typeTransition : node.getTransitionKeys()) {
                updateSubtreeTypeDFS(node.goByTypeSymbol(typeTransition), mask >> 1, parentLevel, level + 1, transition);
            }
        }
    }

}