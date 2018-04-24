// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.ModelElement
import com.intellij.model.ModelReference
import com.intellij.model.ModelService
import com.intellij.model.search.DefaultModelReferenceSearchParameters
import com.intellij.model.search.ModelReferenceSearch
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.QuerySearchRequest
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.CustomProcessorQuery
import com.intellij.util.PairProcessor
import com.intellij.util.Processor

/**
 * Includes [ModelReferenceSearch] results into [ReferencesSearch] results.
 *
 * @see PsiToModelSearchRequestor
 */
class ModelToPsiReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(false) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<PsiReference>) {
    if (queryParameters is PsiToModelSearchRequestor.ModelToPsiParameters) {
      // search started from ModelReferenceSearch
      // -> PsiToModelSearchRequestor queries ReferenceSearch with ModelToPsiParameters
      // -> don't query ModelReferenceSearch again because we started from there
      return
    }
    val modelElement = ModelService.adaptPsiElement(queryParameters.elementToSearch)
    val modelParameters = PsiToModelParameters(modelElement, queryParameters)
    val modelQuery = ModelReferenceSearch.search(modelParameters)
    val psiQuery = CustomProcessorQuery(modelQuery, this::adaptProcessor)
    queryParameters.optimizer.apply {
      val nested = SearchRequestCollector(searchSession)
      val request = QuerySearchRequest(psiQuery, nested, false, PairProcessor { ref, _ -> consumer.process(ref) })
      searchQuery(request)
    }
  }

  private fun adaptProcessor(psiProcessor: Processor<in PsiReference>): Processor<in ModelReference> {
    return Processor { modelReference ->
      if (modelReference is PsiReference) {
        psiProcessor.process(modelReference)
      }
      else {
        TODO("Found reference: $modelReference, class: ${modelReference.javaClass}")
      }
    }
  }

  internal class PsiToModelParameters(
    target: ModelElement,
    psiParameters: ReferencesSearch.SearchParameters
  ) : DefaultModelReferenceSearchParameters(
    psiParameters.project,
    target,
    psiParameters.scopeDeterminedByUser,
    psiParameters.isIgnoreAccessScope
  )
}
