// Simple service for passing information from app-db to JavaScript
// side.
LUPAPISTE.EntriesTitleService = function() {
  "use strict";
  var self = this;

  var info = ko.observable( {} );

  var title = loc( "statistics.entries.title" );

  function getProp( property ) {
    return util.getIn( info, [pageutil.hashApplicationId(), property]);
  }

  self.entriesTitle = ko.pureComputed( function() {
    var n = getProp( "n" );
    return n > 0 ? title + " (" + n + ")" : title;
  });

  self.entriesUnseen = ko.pureComputed (function() {
    return Boolean( getProp( "unseen" ));
  });

  self.setInfo = function( n, unseen ) {
    var m = _.merge( { unseen: Boolean( unseen )},
                     n ? {n: n} : {});
    info(_.set( {}, pageutil.hashApplicationId(), m ));
  };
};
