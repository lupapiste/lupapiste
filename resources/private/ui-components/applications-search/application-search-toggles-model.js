LUPAPISTE.ApplicationSearchTogglesModel = function( params ) {
  "use strict";
  var self = this;

  self.searchType = params.searchType;
  self.searchToggles = _.map( ko.mapping.toJS( params.searchModels ),
                        function( m ) {
                          return { text: m.label,
                                   lText: m.lLabel,
                                   value: m.type
                                 };
                        });
};
