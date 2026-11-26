"""Amont factice de l'API pour l'essai du frontal : rend les ancêtres d'un seul jeton connu, « 'none' » sinon."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import sys

JETON = 'JETON_VALIDE_0123456789'


class Amont(BaseHTTPRequestHandler):
    def do_GET(self):
        jeton = self.headers.get('X-Jeton-Integration')
        self.send_response(204)
        self.send_header('X-Ancetres', 'https://rh.exemple.ca' if jeton == JETON else "'none'")
        self.end_headers()

    def log_message(self, *args):
        pass


HTTPServer(('0.0.0.0', int(sys.argv[1])), Amont).serve_forever()
