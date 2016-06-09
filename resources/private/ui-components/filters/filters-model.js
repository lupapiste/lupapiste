LUPAPISTE.FiltersModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var prefix = _.uniqueId( "filters");

  self.filters = lupapisteApp.services.attachmentsService.filtersArray;

  self.id = function( index ) {
    return prefix + "-" + index;
  };

  self.groupState = self.disposedComputed( function() {
    var activeCount = _.size( _.filter( self.filters(),
                                        function( m ) {
                                          return m.filter();
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
    _.each( self.filters(), function( m ) {
      m.filter( state !== "all");
    });
  };
};
