LUPAPISTE.SummaryLinksModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  var app = params.application;

  function foremanText( m ) {
    var state = loc( m.state );
    var role = _.capitalize( m.foremanRole );
    var name = m.foreman;
    var substitute = m.isSubstituteForeman ? loc( "tyonjohtaja.substitute") : null;
    return util.nonBlankJoin( [role, name, substitute, state], ", " );
  }

  function otherText( m ) {
    var op = loc( "operations." + m.operation);
    var change = m.permitSubtype === "muutoslupa"
        ? loc( "permitSubtype.muutoslupa" ) : null;
    return util.nonBlankJoin( [m.id, op, change], " \u2013 ");
  }

  self.links = self.disposedPureComputed( function() {
    return _.map( ko.mapping.toJS( app.appsLinkingToUs()), function( m ) {

      return {
        text: m.foremanRole ? foremanText( m ) : otherText( m ),
        attr: {"data-test-app-linking-to-us": m.id,
               href: "#!/application/" + m.id,}
      };
    });
  });

  self.removeLinkPermit = app.removeSelectedLinkPermit;
};
