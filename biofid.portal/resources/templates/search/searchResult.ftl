<div class="mt-0 search-state" data-id="${searchState.getSearchId()}">

    <div class="header">
        <div class="flexed w-100 align-items-center justify-content-center">
            <button class="btn selected-btn">
                Suchergebnisse <span class="hits">${searchState.getTotalHits()}</span>
            </button>
        </div>
    </div>

    <div class="row mb-0 mr-0 ml-0 pb-5">

        <div class="col-md-3 position-relative">
            <div class="side-bar-container">
                <div class="side-bar">
                    <div class="content pb-0">

                        <h6 class="text-center underlined mb-4">Taxonomie</h6>
                        <#include "*/search/components/taxonomyTree.ftl">

                    </div>
                </div>
            </div>
        </div>

        <div class="col-md-6 document-list-include">
            <#include "*/search/components/documentList.ftl" >
        </div>

        <div class="col-md-3 position-relative">
            <div class="side-bar-container">
                <div class="side-bar">
                    <div class="content">

                        <h6 class="text-center underlined">Eigennamen</h6>

                        <div class="entry">
                            <div class="flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0"><i class="fas fa-map-marker-alt mr-1"></i> Orte</p>
                                <label class="text mb-0">${searchState.getNamedEntitiesByType("LOCATION")?size}</label>
                            </div>
                        </div>

                        <div class="entry">
                            <div class="flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0"><span><i class="fas fa-user-tag mr-1"></i> Personen</p>
                                <label class="text mb-0">${searchState.getNamedEntitiesByType("PERSON")?size}</label>
                            </div>
                        </div>

                        <div class="entry">
                            <div class="flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0"><i class="fas fa-sitemap mr-1"></i> Organisationen</p>
                                <label class="text mb-0">${searchState.getNamedEntitiesByType("ORGANIZATION")?size}</label>
                            </div>
                        </div>

                        <div class="entry">
                            <div class="flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0"><i class="fas fa-th mr-1"></i> Sonstiges</p>
                                <label class="text mb-0">${searchState.getNamedEntitiesByType("MISC")?size}</label>
                            </div>
                        </div>

                        <hr/>

                        <div class="entry">
                            <div class="flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0"><i class="fas fa-tenge mr-1"></i> Taxone</p>
                                <label class="text mb-0">${searchState.getFoundTaxons()?size}</label>
                            </div>
                        </div>
                        <div class="entry">
                            <div class="flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0"><i class="fas fa-clock mr-1"></i> Zeiten</p>
                                <label class="text mb-0">${searchState.getFoundTimes()?size}</label>
                            </div>
                        </div>

                        <hr/>

                        <h6 class="text-center mb-3 underlined">Navigation</h6>

                        <div class="navigation-include">
                            <#include "*/search/components/navigation.ftl">
                        </div>

                        <hr class="mt-4 mb-4"/>

                        <h6 class="text-center mb-3 underlined">Sortierung</h6>
                        <div class="sort">

                            <div class="mb-2 flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0">Titel</p>
                                <a class="btn mb-0 rounded-a small-font">
                                    <i class="fas fa-sort-amount-up"></i>
                                </a>
                            </div>

                            <div class="mb-2 flexed align-items-center w-100 justify-content-between">
                                <p class="mb-0">Seiten</p>
                                <a class="btn mb-0 rounded-a small-font">
                                    <i class="fas fa-sort-amount-up"></i>
                                </a>
                            </div>

                        </div>

                    </div>
                </div>
            </div>
        </div>
    </div>

</div>