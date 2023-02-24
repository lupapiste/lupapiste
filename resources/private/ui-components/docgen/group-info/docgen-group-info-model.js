// Component that represents both the Docgen group title, help text
// and warnings. The component is instantiated from docmodel.js and
// receives the corresponding params [optional]:
// document: the whole docModel
// schema: group schema
// path: group path
// [help]: HTML. The first help paragraph. The other paragraphs are
// field-specific help texts.
LUPAPISTE.DocgenGroupInfoModel = function( params ) {
  "use strict";

  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var document = params.document;
  var schema = params.schema;
  var path = params.path;
  var help = ko.unwrap(params.help);

  self.warnings = ko.observableArray( docutils.initializeWarnings( document,
                                                                   schema,
                                                                   path ));

  self.groupTitle = self.disposedPureComputed( function() {
    var text = loc( docutils.locKey( document, _.dropRight( path ), schema ));
    return _.isBlank( text ) ? null : _.escape(text);
  });

  self.helpHtml = self.disposedPureComputed( function() {
    var html = help ? [help] : [];
    _.forEach( schema.body, function( field ) {
      var titleKey = docutils.locKey( document, path, field);
      var helpKey = titleKey + ".help";
      if( loc.hasTerm( helpKey )) {
        html.push( sprintf( "<li><strong>%s.</strong> %s</li>",
                            loc( titleKey ), loc( helpKey )));
      }
    });
    if( _.size( html ) ) {
      return "<ul class='help-items'>" + _.join( html, "" ) + "</ul>";
    }
  });

  function updateWarnings( validationResults ) {
    var warns = docutils.updateWarnings( document, path, validationResults );
    if( _.isArray( warns )) {
      self.warnings( warns );
    }
  }

  self.addHubListener( "docgen-validation-results", updateWarnings );
};
