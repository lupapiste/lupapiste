LUPAPISTE.ApplicationsMapMarkerPopupModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.markerInfos = ko.observableArray();
  var popup = null;

  function getIds() {
    return _.map( self.markerInfos(), "id" );
  }

  // Addiitonal information that is not in the original markers
  function fetchInformation() {
    var ids = getIds();
    ajax.query( "map-marker-infos", {ids: ids})
      .success( function(res) {
        if( _.isEqual( ids, getIds())) {
          self.markerInfos( res.infos );
          if( popup ) {
            try {
              popup.updateSize();
            } catch( error ) {
              // Can throw if the popup has already been closed before
              // the query returns.
            }
            popup = null;
          }
        }
      })
      .call();
  }

  self.updateInfos = function( infos ) {
    self.markerInfos( infos );
    fetchInformation();
  };

  self.openApplication = function( app ) {
    pageutil.openApplicationPage( app );
  };

  self.titleText = function() {
    var count = _.size( self.markerInfos() );
    return count  > 1 && loc( "map.application-count", count);
  };

  self.linkText = function( info ) {
    return info.address ? sprintf( "%s: %s", info.id, info.address) : info.id;
  };

  self.dateText = function( locKey, ts ) {
    return loc( locKey ) + " " + util.finnishDate( ts );
  };

  self.bindPopup = function( popper ) {
    popup = popper;
  };
};
