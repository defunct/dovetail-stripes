/* Copyright Alan Gutierrez 2006 */
package com.goodworkalan.dovetail.stripes;

import java.io.IOException;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.goodworkalan.dovetail.MergedRequestWrapper;

/**
 * A servlet filter that initializes the matched pattern cache for each
 * request. The filter will initialize and destroy a cache of match
 * results that will prevent the binding matcher from repeating tests.
 * This is especially useful if a binding queries a database as part of
 * pattern matching.
 * <p>
 * This filter should be included before the <code>StripesFilter</code>
 * in the filter chain for any request that would result in a match
 * against a Dovetail binding.
 * <p>
 * The filter is reentrant. You can use the filter for included and
 * forwarded requests. However, I've not thought out the solution for
 * subsequent pattern matches. The current implementation will merge
 * any further parameters matched by an include or a forward into the
 * query string. 
 *  
 * @author Alan Gutierrez
 */
public class DovetailFilter
implements Filter
{
    /**
     * The <code>ServletContext</code> is retained because the
     * <code>DovetailNameResolver</code> is kept in a servlet context
     * attribute
     */
    private ServletContext servletContext;
    

    /**
     * Initialize the filter. This method retains the
     * <code>ServletContext</code> because <code>DovetailNameResolver</code> is
     * kept in a servlet context attribute.
     */
    public void init(FilterConfig filterConfig) throws ServletException
    {
        this.servletContext = filterConfig.getServletContext();
    }

    /**
     * Prepare the cache of matched parts and merge request parameters.
     *
     * @param request The servlet request.
     * @param response The servlet response.
     * @param filterChain A chain of subsequent filters.
     */
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain filterChain) throws IOException, ServletException
    {
        doFilter((HttpServletRequest) request, response, filterChain);
    }
    
    private void doFilter(HttpServletRequest request, ServletResponse response,
        FilterChain filterChain) throws IOException, ServletException
    {
        DovetailNameResolver resolver = (DovetailNameResolver) servletContext.getAttribute(DovetailNameResolver.class.getCanonicalName());
        resolver.pushRequest();
        try
        {
            Map<String, String[]> parameters = resolver.getParamaters(request);
            if (parameters == null)
            {
                filterChain.doFilter(request, response);
            }
            else
            {
                filterChain.doFilter(new MergedRequestWrapper(request, parameters), response);
            }
        }
        finally
        {
            resolver.popRequest();
        }
    }
    
    /**
     * A no op filter destructor.
     */
    public void destroy()
    {
    }
}

/* vim: set et sw=4 ts=4 ai tw=78 nowrap: */
