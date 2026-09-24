package com.nexus.nexussync.rounds;

import com.nexus.nexussync.params.Fill;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.RoundsParams;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Closing rules of the gate (unit U7): F1 intersection, F2 shared votes and F3 fill.
 *
 * <p>Failed picks contribute no ids. A failed mediator therefore turns F1 into {@code A ∩ B} and
 * makes {@link Fill#MEDIATOR_FIRST} behave as {@link Fill#ALTERNATE_AB}.
 */
public final class Closers {

  private Closers() {}

  /**
   * Round-one closure.
   *
   * <p>{@link Intersection#TRIPLE}: {@code A ∩ B ∩ M} in the order of A. {@link
   * Intersection#PAIR_MEDIATOR_TIEBREAK}: {@code A ∩ B}, with the ids also recommended by the
   * mediator first (in the mediator's order) and the rest after (in the order of A). If the
   * mediator failed, both rules reduce to {@code A ∩ B} in the order of A.
   *
   * @param a pick of persona A
   * @param b pick of persona B
   * @param m recommendation of the mediator
   * @param p gate parameters
   * @return the intersection, without duplicates
   * @implNote O(|A| + |B| + |M|) time and space.
   */
  static List<String> f1(Pick a, Pick b, Pick m, RoundsParams p) {
    List<String> pair = intersect(a, b);
    if (!m.ok()) {
      return pair;
    }
    Set<String> pairSet = new HashSet<>(pair);
    Set<String> mediator = new HashSet<>(m.ids());
    if (p.intersection() == Intersection.TRIPLE) {
      return pair.stream().filter(mediator::contains).toList();
    }
    Set<String> ordered = new LinkedHashSet<>();
    m.ids().stream().filter(pairSet::contains).forEach(ordered::add);
    ordered.addAll(pair);
    return new ArrayList<>(ordered);
  }

  /**
   * Round-two closure: {@code f1} followed by the ids voted by both personas that are not in it.
   *
   * @param f1 result of round one
   * @param va votes of persona A
   * @param vb votes of persona B
   * @return {@code f1 ∪ (VA ∩ VB)}, {@code f1} first, then the shared votes in the order of A
   * @implNote O(|f1| + |VA| + |VB|) time and space.
   */
  static List<String> f2(List<String> f1, Pick va, Pick vb) {
    Set<String> out = new LinkedHashSet<>(f1);
    out.addAll(intersect(va, vb));
    return new ArrayList<>(out);
  }

  /**
   * Fill closure: completes {@code current} up to {@code finalSize} with round-one picks.
   *
   * <p>{@link Fill#ALTERNATE_AB} takes alternately the next unused id of A and of B; {@link
   * Fill#MEDIATOR_FIRST} first takes the mediator's ids in order and then alternates A and B;
   * {@link Fill#NONE} returns {@code current} unchanged.
   *
   * @param current list to complete, kept first and in order
   * @param a pick of persona A
   * @param b pick of persona B
   * @param m recommendation of the mediator
   * @param p gate parameters
   * @return the completed list, at most {@code max(|current|, finalSize)} ids
   * @implNote O(|current| + |A| + |B| + |M|) time and space.
   */
  static List<String> f3(List<String> current, Pick a, Pick b, Pick m, RoundsParams p) {
    Set<String> out = new LinkedHashSet<>(current);
    if (p.fill() == Fill.NONE) {
      return new ArrayList<>(out);
    }
    if (p.fill() == Fill.MEDIATOR_FIRST) {
      addUpTo(out, m.ids(), p.finalSize());
    }
    List<String> ids = alternate(a.ids(), b.ids());
    addUpTo(out, ids, p.finalSize());
    return new ArrayList<>(out);
  }

  /**
   * Intersection of two picks in the order of the first one; a failed pick gives an empty list.
   *
   * @param x first pick
   * @param y second pick
   * @return ids present in both, in the order of {@code x}
   * @implNote O(|x| + |y|) time and space.
   */
  static List<String> intersect(Pick x, Pick y) {
    if (!x.ok() || !y.ok()) {
      return List.of();
    }
    Set<String> other = new HashSet<>(y.ids());
    return x.ids().stream().filter(other::contains).distinct().toList();
  }

  private static List<String> alternate(List<String> first, List<String> second) {
    List<String> out = new ArrayList<>(first.size() + second.size());
    int n = Math.max(first.size(), second.size());
    for (int i = 0; i < n; i++) {
      if (i < first.size()) {
        out.add(first.get(i));
      }
      if (i < second.size()) {
        out.add(second.get(i));
      }
    }
    return out;
  }

  private static void addUpTo(Set<String> out, List<String> ids, int size) {
    for (String id : ids) {
      if (out.size() >= size) {
        return;
      }
      out.add(id);
    }
  }
}
