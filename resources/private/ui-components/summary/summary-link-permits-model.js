LUPAPISTE.SummaryLinkPermitsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  var app = params.application;

  self.linkPermits = self.disposedPureComputed( function() {
    return _.map( app.linkPermitData(), function( m ) {
      var isLink = m.type() === "lupapistetunnus";
      return {
        isLink: isLink,
        id: m.id,
        text: isLink
          ? sprintf( "%s - %s", m.id(),
                     loc( "operations." + m.operation()) )
          : m.id(),
        attr: {"data-test-app-link-permit": m.id(),
               href: isLink ? "#!/application/" + m.id() : null}
      };
    });
  });

  self.removeLinkPermit = app.removeSelectedLinkPermit;
};
