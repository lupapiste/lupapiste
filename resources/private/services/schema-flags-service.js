// Service for fetching and accessing schema flags.
// The flags are referred by `schema-exclude` and `schema-include` schema fields.
LUPAPISTE.SchemaFlagsService = function() {
  "use strict";
  var self = this;

  var flags = ko.observableArray();

  hub.subscribe( "schemaFlagsService::setFlags", function( msg ) {
    flags( msg.flags );
  } );

  self.includeSchema = function( schema ) {
    var exclude = _.get( schema, "schema-exclude" );
    var include = _.get( schema, "schema-include" );
    var isExcluded = _.includes( flags(), exclude );
    var isIncluded = _.includes( flags(), include );

    return schema && (!include || isIncluded) && !isExcluded;
  };

};
