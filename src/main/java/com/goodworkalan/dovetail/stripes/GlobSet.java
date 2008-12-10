package com.goodworkalan.dovetail.stripes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.goodworkalan.dovetail.CoreGlobMapping;
import com.goodworkalan.dovetail.Glob;
import com.goodworkalan.dovetail.GlobCompiler;
import com.goodworkalan.dovetail.GlobMapping;

import net.sourceforge.stripes.action.ActionBean;

public class GlobSet
{
    private final Map<String, String> mapOfExpressions;

    private final Map<Integer, List<Glob>> priorities;
    
    private final Map<Class<? extends ActionBean>, String> patterns;

    public GlobSet()
    {
        this.mapOfExpressions = new HashMap<String, String>();
        this.priorities = new TreeMap<Integer, List<Glob>>();
        this.patterns = new HashMap<Class<? extends ActionBean>, String>();
    }

    public void bind(String pattern, Class<? extends ActionBean> bean,
        int priority, String name)
    {
        Glob glob = new GlobCompiler(bean).compile(pattern);

        priority = -priority;

        List<Glob> listOfGlobs = priorities.get(priority);
        if (listOfGlobs == null)
        {
            listOfGlobs = new ArrayList<Glob>();
            priorities.put(priority, listOfGlobs);
        }

        listOfGlobs.add(glob);
        
        patterns.put(bean, pattern);
    }

    public void remove(String name)
    {
        String pattern = mapOfExpressions.get(name);
        if (pattern != null)
        {
            for (List<Glob> listOfGlobs : priorities.values())
            {
                Iterator<Glob> globs = listOfGlobs.iterator();
                while (globs.hasNext())
                {
                    Glob glob = globs.next();
                    if (glob.getPattern().equals(pattern))
                    {
                        globs.remove();
                    }
                }
            }
        }
    }

    public GlobMapping map(String path)
    {
        for (List<Glob> listOfGlobs : priorities.values())
        {
            for (Glob glob : listOfGlobs)
            {
                CoreGlobMapping mapper = new CoreGlobMapping(glob);
                if (glob.match(mapper, path))
                {
                    return mapper;
                }
            }
        }

        return null;
    }
}
