// This object:
// 1) performs the actual queries for the applications view
// 2) holds the computed observables for the filters attached to the fields in the search component
// 3) performs cleanup actions on those filters
// 4) other miscellaneous things

LUPAPISTE.ApplicationsDataProvider = function(params) {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var defaultData = {applications: [],
                     userTotalCount: -1};
  var defaultSort = {field: "modified", asc: false};
  var defaultForemanSort = {field: "submitted", asc: false};
  var fieldsCache = {};

  // Observables
  self.initialized = ko.observable( false );
  self.sort = params.sort || {field: ko.observable(defaultSort.field), asc: ko.observable(defaultSort.asc)};
  self.data = ko.observable(defaultData);
  self.totalCount = ko.observable(-1);

  self.results = ko.observableArray([]);
  self.searchResultType = ko.observable(params.searchResultType);

  self.mapSupported = true;
  self.mapView = ko.observable( false );
  var blockSearch = params.blockSearch;

  // Have to use strings because radio buttons can't be set back to null and consider it equal to false
  self.userIsGeneralHandler = lupapisteApp.services.roleFilterService.selected;
  if (_.isUndefined(self.userIsGeneralHandler())) {
    self.userIsGeneralHandler("null");
  }
  self.getUserIsGeneralHandlerValue = function() {
    return JSON.parse(self.userIsGeneralHandler());
  };

  self.searchField = lupapisteApp.services.textFilterService.selected;
  self.searchFieldDelayed = self.disposedPureComputed(self.searchField)
    .extend({rateLimit: {method: "notifyWhenChangesStop", timeout: 750}});

  self.limit = params.currentLimit;
  self.skip = ko.observable(0);
  self.pending = ko.observable(false);

  self.searchStartDate = ko.observable();
  self.searchEndDate = ko.observable();

  self.hasResults = self.disposedPureComputed(function() {
    return !_.isEmpty(self.data().applications);
  });

  // Returns true if the given application's organization-specific handling time has been exceeded
  var isOverdueForHandling = function(item) {
    return _.isInteger(item.handlingTimeLeft) && item.handlingTimeLeft < 0;
  };

  // Adds a subheader row in front of a group of application rows and possibly sort them
  var addPartitionRows = function(accumulator, rows) {
    if (_.isEmpty(rows)) {
      return accumulator;
    }
    var subheader = "applications.grouping-subheader.other";
    if (isOverdueForHandling(rows[0])) {
      rows = _.sortBy(rows, "handlingTimeLeft");
      subheader = "applications.grouping-subheader.handling-overdue";
    }
    return _.concat(accumulator, subheader, rows);
  };

  self.resultsWithSubHeaders = self.disposedPureComputed(function() {
    if (self.searchResultType() === "application" // "Hakemukset" tab
        && lupapisteApp.services.organizationsHandlingTimesService.userHasHandlingTimes()) {
      var rows = _.chain(self.results())
        .partition(isOverdueForHandling)
        .reduce(addPartitionRows, [])
        .value();
      // "Other apps" subheader is not needed if it's the only subheader
      if (!_.isEmpty(rows) && rows[0] === "applications.grouping-subheader.other") {
        rows = _.drop(rows, 1);
      }
      return rows;
    }
    return self.results();
  });

  var latestSearchType = null;

  // Application   <-> foremanApplication
  // foremanNotice  -> application
  // construction   -> all
  self.updateSearchResultType = function( searchType ) {
    var current = self.searchResultType();
    latestSearchType = searchType;
    if( searchType === "foreman" ) {
      self.searchResultType( _.get( {application: "foremanApplication",
                                     construction: "all"},
                                    current,
                                    current ));
    } else {
      if( searchType === "applications" && _.startsWith( current, "foreman")) {
        self.searchResultType( "application");
      }
    }
  };

  var dashboardFields = ko.observable();
  self.searchFields = self.disposedComputed( function() {
    return _.merge( {
      limit: self.limit(),
      skip: self.skip(),
      applicationType: self.searchResultType(),
    },
                    dashboardFields(),
                    {sort: {field: self.sort.field(),
                            asc: self.sort.asc() }});
  });

  self.disposedComputed(function() {
    self.searchResultType();
    self.limit();
    self.sort.field();
    self.sort.asc();
    self.skip(0); // when above filters change, set table view to first page
  });


  // Subscriptions
  lupapisteApp.services.applicationFiltersService.selected.subscribe(function(selected) {
    if (selected) {
      self.sort.field(selected.sort.field());
      self.sort.asc(selected.sort.asc());
    }
  });

  // Methods
  function wrapData(data) {
    data.applications = _.map(data.applications, function(item) {
      // Add urgency icons
      switch(item.urgency) {
        case "urgent":
          item.urgencyClass = "lupicon-warning";
          break;
        case "normal":
          item.urgencyClass = "lupicon-document-list";
          break;
        case "pending":
          item.urgencyClass = "lupicon-circle-dash";
          break;
      }
      // Calculate handling time left (in days since submission)
      if (item.state === "submitted") {
        item.handlingTimeLeft = lupapisteApp.services.organizationsHandlingTimesService.getTimeLeft(item.organization, item.submitted);
      }

      return item;
    });
    return data;
  }

  self.onSuccess = function(res) {
    var data = wrapData(res.data);
    self.data(data);
    self.results(data.applications);
  };

  self.clearFilters = function() {
    lupapisteApp.services.handlerFilterService.selected([]);
    lupapisteApp.services.tagFilterService.selected([]);
    lupapisteApp.services.companyTagFilterService.selected([]);
    lupapisteApp.services.operationFilterService.selected([]);
    lupapisteApp.services.organizationFilterService.selected([]);
    lupapisteApp.services.areaFilterService.selected([]);
    lupapisteApp.services.applicationFiltersService.selected(undefined);
    lupapisteApp.services.eventFilterService.selected([]);
    self.userIsGeneralHandler("null");
    self.searchStartDate("");
    self.searchEndDate("");
    self.searchField("");
  };

  self.setDefaultSort = function() {
    self.sort.field(defaultSort.field);
    self.sort.asc(defaultSort.asc);
  };

  self.setDefaultForemanSort = function() {
    self.sort.field(defaultForemanSort.field);
    self.sort.asc(defaultForemanSort.asc);
  };

  function cacheMiss() {
    return !_.isEqual(_.omitBy( self.searchFields(), _.isNil ),
                      _.omitBy( fieldsCache, _.isNil ));
  }


  // Actual data provided is retrieved here
  self.fetchSearchResults = function ( clearCache ) {
    if( lupapisteApp.models.currentUser.loaded()
        && lupapisteApp.models.globalAuthModel.isInitialized()
        && lupapisteApp.services.organizationsUsersService.isInitialized()
        && !self.mapView()
        && !blockSearch()) {
      if( clearCache ) {
        fieldsCache = {};
      }
      // Create dependency to the observable
      var fields = self.searchFields();
      var currentSearchType = latestSearchType;
      if(cacheMiss()) {
        ajax.datatables("applications-search", fields)
        .success(function( res ) {
          if( currentSearchType === latestSearchType ) {
            fieldsCache = _.cloneDeep(fields);
            self.onSuccess( res );
          }
        })
        .onError("error.unauthorized", notify.ajaxError)
        .pending(self.pending)
        .complete( function() {
          self.initialized( true );
        })
        .call();
        ajax.datatables("applications-search-total-count", fields)
        .success(function( res ) {
          self.totalCount(res.data.totalCount);
        })
        .onError("error.unauthorized", notify.ajaxError)
        .call();
      }
    }
  };

  self.disposedComputed( self.fetchSearchResults ).extend({deferred: true});

  self.selectedStates = self.disposedPureComputed( function() {
    var states = _( self.searchFields().states )
        .map( loc )
        .sort()
        .value();
    return _.isEmpty( states ) ? null : states;
  });

  // Communication with dashboard

  var signalSort = _.debounce( function() {
    hub.send( "ApplicationsDataProvider::sortChanged",
              {sort: ko.mapping.toJS( self.sort )});
  }, 100);

  self.disposedSubscribe( self.sort.field, signalSort );
  self.disposedSubscribe( self.sort.asc, signalSort );

  self.addHubListener( "Dashboard::search-applications",
                       function( event ) {
                         // New search, reset skip.
                         self.skip( 0 );
                         self.sort.field( event.fields.sort.field );
                         self.sort.asc( event.fields.sort.asc );
                         blockSearch( false );
                         dashboardFields( event.fields );
                       });
};
