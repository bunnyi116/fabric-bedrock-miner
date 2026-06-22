package com.github.bunnyi116.bedrockminer.util;

import java.util.Iterator;
import java.util.List;

public class CombinedIterator<T> implements Iterator<T> {
    private final Iterator<T> iterator1;
    private final Iterator<T> iterator2;
    private boolean lastFromIterator1;

    public CombinedIterator(List<T> list1, List<T> list2) {
        this.iterator1 = list1.iterator();
        this.iterator2 = list2.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator1.hasNext() || iterator2.hasNext();
    }

    @Override
    public T next() {
        if (iterator1.hasNext()) {
            lastFromIterator1 = true;
            return iterator1.next();
        } else {
            lastFromIterator1 = false;
            return iterator2.next();
        }
    }

    @Override
    public void remove() {
        if (lastFromIterator1) {
            iterator1.remove();
        } else {
            iterator2.remove();
        }
    }

    public Iterator<T> getIterator1() {
        return iterator1;
    }

    public Iterator<T> getIterator2() {
        return iterator2;
    }
}
