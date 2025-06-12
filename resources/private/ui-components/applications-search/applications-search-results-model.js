LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.dataProvider = params.dataProvider;

  function foremanTasksText(tyotehtavat) {
    var OTHER_KEY = "osapuoli.tyonjohtaja.vastattavatTyotehtavat.muuMika";

    return _.map(tyotehtavat.i18nkeys, function(key) {
      var text = loc(key);
      if (key === OTHER_KEY && tyotehtavat.muuMikaValue) {
        text += ": " + tyotehtavat.muuMikaValue;
      }
      return text;
    }).join(", ");
  }

  self.data = ko.pureComputed(function() {
    return _.map(self.dataProvider.resultsWithSubHeaders(), function(item) {
      if (!_.isString(item)) { // Not a subheader
        item.searchType = self.dataProvider.searchResultType();
        if (item.foremanRole) {
          item.foremanRoleI18nkey = "osapuoli.tyonjohtaja.kuntaRoolikoodi." + item.foremanRole;
        }
        if (item.tyotehtavat && item.tyotehtavat.i18nkeys) {
          item.foremanTasksText = foremanTasksText(item.tyotehtavat);
        }
      }
      return item;
    });
  });

  self.gotResults = params.gotResults;

  self.selectedTab = self.dataProvider.searchResultType;

  self.useElasticsearch = self.dataProvider.useElasticsearch;

  // foreman elastic documents have application-id
  self.coerceAppModel = function(model) {
    if (_.isObject(model)) {
      if (model["application-id"]) {
        model.id = model["application-id"];
      }
    }
    return model;
  };

  self.openApplication = function(model, event, target) {
    pageutil.openApplicationPage(self.coerceAppModel(model), target);
  };

  self.keyOpenApplication = function( model, event, target ) {
    if( event.keyCode === 13 ) {
      pageutil.openApplicationPage(self.coerceAppModel(model), target);
    }
  };

  self.dispose = _.partial(hub.unsubscribe, self.onPageLoad);

  self.disposedComputed(function () {
    ko.mapping.toJS(self.dataProvider.sort);
    self.dataProvider.skip(0);
  });

  self.isProcessing = self.disposedPureComputed( function() {
    return self.dataProvider.searchResultType() === "processing"
      && !self.dataProvider.selectedStates();
  });

  self.procComplexity = function( data ) {
    var complexity = _.get( data, "processing.complexity");
    var months = _.get( {"special": 1,
                         "small": 3,
                         "medium": 3,
                         "large": 3,
                         "very-large": 6,
                         "extra-large": 6},
                        complexity );
    return months
      ? sprintf( "%s (%d %s)",
                 loc( "pate.complexity." + complexity),
                 months, loc( "unit.kk" ))
      : "";
  };

  function periodPart( data, period, field ) {
    var n = _.get( data, ["processing", period, field]);
        return n ? loc( "statistics.period." + field, n) : null;
  }

  function procPeriod( data, periodField ) {
    var period = _( ["y", "m", "d"] )
        .map( _.partial( periodPart, data, periodField ))
        .filter()
        .join( " " );
    return period || loc( "statistics.period.d", 0 );
  }

  self.procDays = function( data, field ) {
    var s = _.get( data, "processing." + field);
    return s ? loc( "statistics.period.d", s) : "";
  };

  self.procRemaining = function( data ) {
    var deadline = _.get( data, "processing.deadline" );
    var period = procPeriod( data, "remaining-period");
    return deadline ? period + " (" + util.finnishDate( deadline ) + ")" : "";
  };

  self.procClass = function( data ) {
    var n = _.get( data, "processing.remaining" );
    return (n && n < 0) ? "txt--bold fg--negative" : null;
  };

  self.procStatus = function( data ) {
    var s = _.get( data, "processing.status");
    return s ? loc( "statistics.processing.status." + s) : "";
  };

  self.procRequests = function( data ) {
    var n = _.get( data, "processing.requests", 0);
    return loc( "statistics.processing.kpl", n);
  };

  self.procUnseen = function( data ) {
    return _.get( data, "processing.unseen?" );
  };

  function isAuthority() {
    return lupapisteApp.models.currentUser.isAuthority();
  }

  function indicatorsColumn( authorityColumns ) {
    return util.createSortableColumn("first", "applications.indicators",
                                     {colspan: isAuthority()
                                      ? authorityColumns : authorityColumns - 1,
                                      sortable: false,
                                      currentSort: self.dataProvider.sort});
  }

  self.columns = self.disposedPureComputed( function() {

    var indicators = indicatorsColumn( 6 );
    var type = util.createSortableColumn("second", "applications.type",
                                         {sortField: "type",
                                          currentSort: self.dataProvider.sort});
    var lox = util.createSortableColumn("third", "applications.location",
                                        {sortField: "location",
                                         currentSort: self.dataProvider.sort});
    var ops = util.createSortableColumn("fourth", "applications.operation",
                                        {sortable: false,
                                         currentSort: self.dataProvider.sort});
    var applicant = util.createSortableColumn("fifth", "applications.applicant",
                                              {sortField: "applicant",
                                               currentSort: self.dataProvider.sort});
    var submitted = util.createSortableColumn("sixth", "applications.submitted",
                                              {sortField: "submitted",
                                               currentSort: self.dataProvider.sort});
    var updated = util.createSortableColumn("seventh", "applications.updated",
                                            {sortField: "modified",
                                             currentSort: self.dataProvider.sort});
    var status = util.createSortableColumn("eight",   "applications.status",
                                           {sortField: "state",
                                            currentSort: self.dataProvider.sort});
    var handlers = util.createSortableColumn("ninth", "application.handlers",
                                             {sortField: "handler",
                                              currentSort: self.dataProvider.sort});
    var verdict = util.createSortableColumn("sixth", "pate-dates.verdict-date",
                                            {sortField: "verdictDate",
                                             currentSort: self.dataProvider.sort});
    // Processing

    var procIndicators = indicatorsColumn( 7 );

    var procComplexity = util.createSortableColumn("tenth", "statistics.processing.complexity",
                                                   {sortField: "proc-complexity",
                                                    currentSort: self.dataProvider.sort});
    var procStatus = util.createSortableColumn("eleventh", "statistics.processing.status",
                                               {sortField: "proc-status",
                                                currentSort: self.dataProvider.sort});
    var procRequests = util.createSortableColumn("twelfth", "statistics.processing.requests",
                                                  {sortField: "proc-requests",
                                                   currentSort: self.dataProvider.sort});
    var procRemaining = util.createSortableColumn("thirteenth", "statistics.processing.remaining",
                                                  {sortField: "proc-remaining",
                                                   currentSort: self.dataProvider.sort});


    if( self.isProcessing() ) {
      return [procIndicators, lox, ops, applicant, updated, procComplexity,
              procStatus, procRequests, procRemaining];
    }
    var shouldShowVerdictDate = _.includes(["construction", "verdict"], self.selectedTab());
    return  [indicators, type, lox, ops, applicant,
             shouldShowVerdictDate ? verdict : submitted,
             updated, status, handlers];
  });

  self.rowWidth = self.disposedComputed(function() {
    // The indicators column spans multiple columns.
    var spansize = 5 + _.size( _.filter( [isAuthority(), self.isProcessing()]));
    return _.size(self.columns()) + spansize;
  });

  self.statementSummaryText = function(statementSummary) {
    var openCount = statementSummary.open.length;
    var givenCount = statementSummary.given.length;
    var openLocKey = openCount === 1 ? "statements.open.single" : "statements.open";
    var givenLocKey = givenCount === 1 ? "statements.given.single" : "statements.given";
    var openText = loc(openLocKey, openCount);
    var givenText = loc(givenLocKey, givenCount);
    if (openCount) {
      if (givenCount) {
        // if there are both open and given, show both texts
        return openText + "\n" + givenText;
      } else {
        return openText;
      }
    } else if (givenCount) {
      return givenText;
    } else { // fallback if something is wrong with the data
      return loc("not-known");
    }
  };

  // Scroll support.
  hub.send( "scrollService::follow", {hashRe: /\/applications$/} );


  // Scroll position support
  self.scrollPop = _.debounce( function()  {
    _.defer( hub.send,  "scrollService::pop", {name: "search-results"});
  }, 100 );

};
