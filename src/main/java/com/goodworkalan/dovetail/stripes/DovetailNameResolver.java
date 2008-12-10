/* Copyright Alan Gutierrez 2006 */
package com.goodworkalan.dovetail.stripes;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.SessionScope;
import net.sourceforge.stripes.config.Configuration;
import net.sourceforge.stripes.controller.NameBasedActionResolver;
import net.sourceforge.stripes.controller.UrlBinding;
import net.sourceforge.stripes.controller.UrlBindingFactory;
import net.sourceforge.stripes.exception.ActionBeanNotFoundException;
import net.sourceforge.stripes.exception.StripesRuntimeException;
import net.sourceforge.stripes.exception.StripesServletException;
import net.sourceforge.stripes.util.Log;

import com.goodworkalan.dovetail.Glob;
import com.goodworkalan.dovetail.GlobMapping;
import com.goodworkalan.dovetail.GlobSet;


public class DovetailNameResolver
extends NameBasedActionResolver
{
    private ServletContext servletContext;
    
    /** Log instance used to log information from this class. */
    private static final Log log = Log.getInstance(DovetailNameResolver.class);
    
    private Map<Class<? extends ActionBean>, String> paths = new HashMap<Class<? extends ActionBean>, String>();
    
    private Map<Class<? extends ActionBean>,Map<String,Method>> eventMappings = new HashMap<Class<? extends ActionBean>, Map<String,Method>>();
    
    private GlobSet globs = new GlobSet();
    
    private ThreadLocal<MatchCacheCounter> matchCache = new ThreadLocal<MatchCacheCounter>()
    {
        @Override
        protected MatchCacheCounter initialValue()
        {
            return new MatchCacheCounter(0);
        }
    };

    public void pushRequest()
    {
        MatchCacheCounter counter = matchCache.get();
        if (counter.count == 0)
        {
            matchCache.set(new MatchCacheCounter(1));
        }
        else
        {
            counter.count++;
        }
    }
    
    public void popRequest()
    {
        MatchCacheCounter counter = matchCache.get();
        counter.count--;
    }

    @Override
    public void init(Configuration configuration) throws Exception
    {
        servletContext = configuration.getServletContext();
        servletContext.setAttribute(DovetailNameResolver.class.getCanonicalName(), this);
        super.init(configuration);
    }
    
    @Override
    protected void addActionBean(Class<? extends ActionBean> clazz)
    {
        DovetailBinding binding = clazz.getAnnotation(DovetailBinding.class);
        if (binding != null)
        {
            addActionBean(clazz, binding.priority(), binding.value());
            Map<String, Method> classMappings = new HashMap<String, Method>();
            processMethods(clazz, classMappings);

            // Put the event->method mapping for the class into the set of mappings
            eventMappings.put(clazz, classMappings);
        }
        super.addActionBean(clazz);
    }

    private void addActionBean(Class<? extends ActionBean> bean, int priority, String pattern)
    {
        globs.bind(pattern, bean, priority, null);
    }

    @SuppressWarnings("unchecked")
    public ActionBean getActionBean(ActionBeanContext context, String path) throws StripesServletException
    {
        GlobMapping mapping = globs.map(path);

        if (mapping == null)
        {
            return super.getActionBean(context, path);
        }
        
        Class<? extends ActionBean> beanClass = (Class<? extends ActionBean>) mapping.getGlob().getConditionalClass();

        ActionBean bean;

        if (beanClass == null)
        {
            throw new ActionBeanNotFoundException(
                    path, UrlBindingFactory.getInstance().getPathMap());
        }

        try
        {
            Glob glob = mapping.getGlob();
            HttpServletRequest request = context.getRequest();

            if (beanClass.isAnnotationPresent(SessionScope.class))
            {
                bean = (ActionBean) request.getSession().getAttribute(glob.getPattern());

                if (bean == null)
                {
                    bean = makeNewActionBean(beanClass, context);
                    request.getSession().setAttribute(glob.getPattern(), bean);
                }
            }
            else
            {
                bean = (ActionBean) request.getAttribute(glob.getPattern());
                if (bean == null)
                {
                    bean = makeNewActionBean(beanClass, context);
                    request.setAttribute(glob.getPattern(), bean);
                }
            }

            request.setAttribute(path, bean);

            setActionBeanContext(bean, context);
        }
        catch (Exception e)
        {
            StripesServletException sse = new StripesServletException(
                "Could not create instance of ActionBean type [" + beanClass.getName() + "].", e);
            log.error(sse);
            throw sse;
        }

        assertGetContextWorks(bean);
        return bean;
    }

    public ActionBean getActionBean(ActionBeanContext context)
        throws StripesServletException
    {
        HttpServletRequest request = context.getRequest();
        UrlBinding binding = UrlBindingFactory.getInstance().getBindingPrototype(request);
        String path = binding == null ? getRequestedPath(request) : binding.getPath();
        ActionBean bean = getActionBean(context, path);
        GlobMapping mapping = globs.map(path);
        if (mapping == null)
        {
            request.setAttribute(RESOLVED_ACTION, super.getUrlBindingFromPath(path));
        }
        else
        {
            request.setAttribute(RESOLVED_ACTION, mapping.getGlob().getPattern());
        }
        return bean;
    }
    
    protected String trimContextPath(HttpServletRequest request)
    {
        // Trim context path from beginning of URI
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath.length() > 1)
            uri = uri.substring(contextPath.length());

        // URL decode
        try
        {
            String encoding = request.getCharacterEncoding();
            uri = URLDecoder.decode(uri, encoding != null ? encoding : "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new StripesRuntimeException(e);
        }

        return uri;
    }

    public Collection<Class<? extends ActionBean>> getActionBeanClasses()
    {
        return super.getActionBeanClasses();
    }
    
    public Map<String, String[]> getParamaters(HttpServletRequest request)
    {
        GlobMapping mapping = globs.map(getRequestedPath(request));
        if (mapping != null)
        {
            return mapping.getParameters();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends ActionBean> getActionBeanType(String path)
    {
        GlobMapping mapping = globs.map(path);
        if (mapping != null)
        {
            return (Class<? extends ActionBean>) mapping.getGlob().getConditionalClass();
        }
        return super.getActionBeanType(path);
    }

    public Method getDefaultHandler(Class<? extends ActionBean> bean) throws StripesServletException
    {
        return super.getDefaultHandler(bean);
    }

    public String getEventName(Class<? extends ActionBean> bean, ActionBeanContext context)
    {
        return super.getEventName(bean, context);
    }

    public String getHandledEvent(Method handler)
    {
        return super.getHandledEvent(handler);
    }

    public Method getHandler(Class<? extends ActionBean> bean, String eventName) throws StripesServletException
    {
        return super.getHandler(bean, eventName);
    }
    
//    protected GlobMapping getGlobMappingFromRequest(HttpServletRequest request)
//    {
//        String path = getRequestedPath(request);
//        Map<String, GlobMapping> mappings = getGlobMappings(request);
//        if (mappings == null)
//        {
//            mappings = new HashMap<String, GlobMapping>();
//            request.setAttribute(DovetailNameResolver.class.getName(), mappings);
//        }
//        if (!mappings.containsKey(path))
//        {
//            mappings.put(path, GlobFactory.getInstance(request.getSession().getServletContext()).map(path));
//        }
//        return mappings.get(path);
//    }
    
    @SuppressWarnings("unchecked")
    protected Map<String, GlobMapping> getGlobMappings(HttpServletRequest request)
    {
        return (Map<String, GlobMapping>) request.getAttribute(DovetailNameResolver.class.getName());
    }
    
    protected String getEventNameFromPath(Class<? extends ActionBean> bean,
                                          ActionBeanContext context)
    {
        GlobMapping mapping = globs.map(getRequestedPath(context.getRequest()));
        
        if (mapping == null)
        {
            return super.getEventNameFromPath(bean, context);
        }
        
        String[] parameters = mapping.getParameters().get("event");
        if (parameters == null || parameters.length == 0)
        {
            return null;
        }
        return parameters[0];
    }

    public String getUrlBinding(Class<? extends ActionBean> clazz)
    {
        if (paths.containsKey(clazz))
        {
            return paths.get(clazz);
        }
        return super.getUrlBinding(clazz);
    }

    public String getUrlBindingFromPath(String path)
    {
        GlobMapping mapping = globs.map(path);

        if (mapping != null)
        {
            return path;
        }
        
        return super.getUrlBindingFromPath(path);
    }
}

/* vim: set et sw=4 ts=4 ai tw=78 nowrap: */