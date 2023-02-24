// Organization links component. Note: This is only used on the last
// create page and inforequests. Side panel info links are in their
// own component.
// Parameters [optional]_
// [organization]: if given the links are taken from its links
// property, otherwise from InfoService.
LUPAPISTE.OrganizationLinksModel = function( params) {
  "use strict";
  var self = this;

  params = params || {};

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.organizationLinks = self.disposedPureComputed( function() {
    if( params.organization ) {
      return _.map( util.getIn( params, ["organization", "links"] ),
                    function( link ) {
                      var lang = loc.getCurrentLanguage();
                      return {text: _.get( link, ["name", lang]),
                              url: _.get( link, ["url", lang ])};
                    });
    } else {
      return lupapisteApp.services.infoService.organizationLinks();
    }
  });
};
