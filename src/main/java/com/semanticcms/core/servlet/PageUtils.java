/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-servlet.
 *
 * semanticcms-core-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.servlet;

import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Utilities for working with pages.
 */
final public class PageUtils {

	public static boolean hasChild(Page page) {
		for(PageRef childRef : page.getChildPages()) {
			if(childRef.getBook() != null) return true;
		}
		return false;
	}

	public static boolean hasElement(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		Class<? extends Element> elementType,
		boolean recursive
	) throws ServletException, IOException {
		return hasElementRecursive(
			servletContext,
			request,
			response,
			page,
			elementType,
			recursive,
			recursive ? new HashSet<PageRef>() : null
		);
	}

	private static boolean hasElementRecursive(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		Class<? extends Element> elementType,
		boolean recursive,
		Set<PageRef> seenPages
	) throws ServletException, IOException {
		for(Element element : page.getElements()) {
			if(elementType.isAssignableFrom(element.getClass())) {
				return true;
			}
		}
		if(recursive) {
			seenPages.add(page.getPageRef());
			for(PageRef childRef : page.getChildPages()) {
				if(
					// Child not in missing book
					childRef.getBook() != null
					// Not already seen
					&& !seenPages.contains(childRef)
				) {
					if(
						hasElementRecursive(
							servletContext,
							request,
							response,
							CapturePage.capturePage(servletContext, request, response, childRef, CaptureLevel.META),
							elementType,
							recursive,
							seenPages
						)
					) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * <p>
	 * Finds the allowRobots setting for the given page.
	 * </p>
	 * <p>
	 * When no allowRobots provided, will use the allowRobots(s) of any parent
	 * page that is within the same book.  If there are no parent pages
	 * in this same book, uses the book's allowRobots.
	 * </p>
	 * <p>
	 * When inheriting allowRobots from multiple parent pages, the allowRobots must
	 * be in exact agreement.  This means exactly the same order and all
	 * values matching precisely.  Any mismatch in allowRobots will result in
	 * an exception.
	 * </p>
	 */
	public static boolean findAllowRobots(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Page page
	) throws ServletException, IOException {
		return findAllowRobotsRecursive(
			servletContext,
			request,
			response,
			page,
			new HashMap<PageRef,Boolean>()
		);
	}

	private static boolean findAllowRobotsRecursive(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Page page,
		Map<PageRef,Boolean> finished
	) throws ServletException, IOException {
		PageRef pageRef = page.getPageRef();
		assert !finished.containsKey(pageRef);
		// Use directly set allowRobots first
		Boolean pageAllowRobots = page.getAllowRobots();
		if(pageAllowRobots == null) {
			// Use the allowRobots of all parents in the same book
			Book book = pageRef.getBook();
			for(PageRef parentRef : page.getParentPages()) {
				if(book.equals(parentRef.getBook())) {
					// Check finished already
					Boolean parentAllowRobots = finished.get(parentRef);
					if(parentAllowRobots == null) {
						// Capture parent and find its allowRobots
						parentAllowRobots = findAllowRobotsRecursive(
							servletContext,
							request,
							response,
							CapturePage.capturePage(servletContext, request, response, parentRef, CaptureLevel.PAGE),
							finished
						);
					}
					if(pageAllowRobots == null) {
						pageAllowRobots = parentAllowRobots;
					} else {
						// Must precisely match when have multiple parents
						if(!pageAllowRobots.equals(parentAllowRobots)) throw new ServletException("Mismatched allowRobots inherited from different parents: " + pageAllowRobots + " does not match " + parentAllowRobots);
					}
				}
			}
			// No parents in the same book, use book allowRobots
			if(pageAllowRobots == null) pageAllowRobots = book.getAllowRobots();
		}
		// Store in finished
		finished.put(pageRef, pageAllowRobots);
		return pageAllowRobots;
	}

	/**
	 * Make no instances.
	 */
	private PageUtils() {
	}
}
