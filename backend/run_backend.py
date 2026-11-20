from dotenv import load_dotenv
load_dotenv()
import app

if __name__ == '__main__':
    app.POOL = __import__('multiprocessing').Pool(processes=__import__('multiprocessing').cpu_count())
    app.start_background()
    app.app.run(host='127.0.0.1', port=int(__import__('os').environ.get('SSA_PORT','5000')), threaded=True, use_reloader=False)
