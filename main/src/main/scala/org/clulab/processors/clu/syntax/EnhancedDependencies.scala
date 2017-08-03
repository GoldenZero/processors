package org.clulab.processors.clu.syntax

import org.clulab.processors.Sentence
import org.clulab.struct.{DirectedGraph, DirectedGraphIndex, Edge}

import scala.collection.mutable.ListBuffer

/**
  * Converts Stanford basic dependencies to collapsed ones
  * This follows the rules from http://universaldependencies.org/u/overview/enhanced-syntax.html
  *   (but applied to Stanford deps rather than universal ones)
  * We support:
  * - Collapsing of prepositions to the prep_* label
  * - Controlled/raised subjects
  * - Propagate subjects and objects in conjoined verbs
  * - Propagate conjoined subjects and objects to same verb
  * - Push subjects/objects inside relative clauses
  * User: mihais
  * Date: 8/1/17
  */
object EnhancedDependencies {
  def generateEnhancedDependencies(sentence:Sentence, dg:DirectedGraph[String]): DirectedGraph[String] = {
    val dgi = dg.toDirectedGraphIndex
    collapsePrepositions(sentence, dgi)
    raiseSubjects(dgi)
    propagateSubjectsAndObjectsInConjVerbs(sentence, dgi)
    propagateConjSubjectsAndObjects(dgi)
    pushSubjectsObjectsInsideRelativeClauses(dgi)
    dgi.toDirectedGraph
  }

  /**
    * Collapses prep + pobj into prep_x
    * Mary gave a book to Jane => prep_to from 1 to 5
    * @param sentence
    * @param dgi
    */
  def collapsePrepositions(sentence:Sentence, dgi:DirectedGraphIndex[String]) {
    val toRemove = new ListBuffer[Edge[String]]
    val preps = dgi.findByName("prep")
    for(prep <- preps) {
      toRemove += prep
      val word = sentence.words(prep.destination)
      for(pobj <- dgi.findByName("pobj").filter(_.source == prep.destination)) {
        dgi.addEdge(prep.source, pobj.destination, s"prep_$word")
        toRemove += pobj
      }
    }
    toRemove.foreach(e => dgi.removeEdge(e.source, e.destination, e.relation))
  }

  /**
    * Pushes subjects inside xcomp clauses
    * Mary wants to buy a book => nsubj from 3 to 0
    * @param dgi
    */
  def raiseSubjects(dgi:DirectedGraphIndex[String]) {
    val subjects = dgi.findByName("nsubj")
    for(se <- subjects) {
      for(xcomp <- dgi.findByName("xcomp").filter(_.source == se.source)) {
        dgi.addEdge(xcomp.destination, se.destination, "nsubj")
      }
    }
  }

  /**
    * Propagates subjects/objects between conjoined verbs
    * The store buys and sells cameras => nsubj from 2 to 1 and from 4 to 1; dobj from 2 to 5 and from 4 to 5
    * @param dgi
    */
  def propagateSubjectsAndObjectsInConjVerbs(sentence:Sentence, dgi:DirectedGraphIndex[String]) {
    val conjs = dgi.findByName("conj").sortBy(_.source)
    val tags = sentence.tags.get
    for(conj <- conjs) {
      val left = math.min(conj.source, conj.destination)
      val right = math.max(conj.source, conj.destination)
      if(tags(left).startsWith("VB") && tags(right).startsWith("VB")) { // two verbs

        // add the subject of the left verb to the right, if the right doesn't have a subject already
        val leftSubjs = dgi.findByHeadAndName(left, "nsubj")
        val rightSubjs = dgi.findByHeadAndName(right, "nsubj")
        if(leftSubjs.nonEmpty && rightSubjs.isEmpty) {
          for(s <- leftSubjs) {
            dgi.addEdge(right, s.destination, "nsubj")
          }
        }

        // add the dobj of the right verb to the left, if the left doesn't have a dobj already
        val leftObjs = dgi.findByHeadAndName(left, "dobj")
        val rightObjs = dgi.findByHeadAndName(right, "dobj")
        if(leftObjs.isEmpty && rightObjs.nonEmpty) {
          for(o <- rightObjs) {
            dgi.addEdge(left, o.destination, "dobj")
          }
        }

        // TODO: add nsubjpass, add prep_*

      }
    }
  }

  def propagateConjSubjectsAndObjects(dgi:DirectedGraphIndex[String]) {
    // TODO
  }
  
  def pushSubjectsObjectsInsideRelativeClauses(dgi:DirectedGraphIndex[String]) {
    // TODO
  }
}
