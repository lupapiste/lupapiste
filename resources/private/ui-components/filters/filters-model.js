LUPAPISTE.FiltersModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var prefix = _.uniqueId( "filters");

  var filterSet = lupapisteApp.services.attachmentsService.getFilters( params.pageName );

  self.filters = self.disposedPureComputed(function() {
    return _.flatten(filterSet.filters());
  });

  self.id = function( index ) {
    return prefix + "-" + index;
  };

  self.groupState = self.disposedComputed( function() {
    var activeCount = _.size( _.filter( self.filters(),
                                        function( filter ) {
                                          return filter.active();
                                        }));
    var state = "some";
    if( activeCount === _.size( self.filters()) ) {
      state = "all";
    }
    if( !activeCount ) {
      state = "none";
    }
    return state;
  });

  self.clickGroupState = function() {
    var state = self.groupState();
    filterSet.toggleAll( state !== "all" );
  };
};
